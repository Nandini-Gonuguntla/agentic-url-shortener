package com.agentic.orchestrator.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copies the target project (without build output) plus the Maven wrapper into a fresh directory,
 * initialises a private git repository and commits the baseline.
 */
public class GitWorkspaceFactory implements WorkspaceFactory {

    private static final Set<String> EXCLUDED_DIRS = Set.of("target", ".git", ".idea", "node_modules", "runs");
    private static final List<String> WRAPPER_FILES = List.of("mvnw", "mvnw.cmd", ".mvn");

    private final Path sourceProject;
    private final Path wrapperRoot;
    private final ProcessRunner processes;

    public GitWorkspaceFactory(Path sourceProject, Path wrapperRoot, ProcessRunner processes) {
        this.sourceProject = sourceProject;
        this.wrapperRoot = wrapperRoot;
        this.processes = processes;
    }

    @Override
    public Workspace create(Path target) {
        try {
            if (Files.exists(target) && Files.list(target).findAny().isPresent()) {
                throw new IllegalStateException("Workspace directory is not empty: " + target);
            }
            copyTree(sourceProject, target);
            for (String name : WRAPPER_FILES) {
                Path source = wrapperRoot.resolve(name);
                if (Files.exists(source)) {
                    copyTree(source, target.resolve(name));
                }
            }
            Path gitignore = target.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, "target/\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create workspace at " + target, e);
        }
        git(target, "init", "-q");
        git(target, "config", "user.name", "agentic-orchestrator");
        git(target, "config", "user.email", "orchestrator@agentic.local");
        git(target, "config", "core.autocrlf", "false");
        git(target, "add", "-A");
        git(target, "commit", "-q", "-m", "baseline: " + sourceProject.getFileName());
        String baseline = git(target, "rev-parse", "HEAD").strip();
        Set<String> files = new HashSet<>(git(target, "ls-files").lines().filter(l -> !l.isBlank()).toList());
        return new Workspace(target.toAbsolutePath().normalize(), processes, baseline, files);
    }

    private String git(Path dir, String... args) {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        ProcessRunner.Result result = processes.run(command, dir, Duration.ofMinutes(2), Map.of("GIT_TERMINAL_PROMPT", "0"));
        if (!result.ok()) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + result.tail(10));
        }
        return result.output();
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && EXCLUDED_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().endsWith(".iml")) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
