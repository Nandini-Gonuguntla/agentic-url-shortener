package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.artifact.FileChange;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An isolated git repository holding a copy of the target project for one run. Every accepted
 * change is a commit, so rollback is {@code git reset --hard <checkpoint>} and the final
 * deliverable is a reviewable diff against the baseline commit.
 */
public final class Workspace {

    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(2);

    private final Path root;
    private final ProcessRunner processes;
    private final String baseline;
    private final Set<String> baselineFiles;

    Workspace(Path root, ProcessRunner processes, String baseline, Set<String> baselineFiles) {
        this.root = root;
        this.processes = processes;
        this.baseline = baseline;
        this.baselineFiles = Set.copyOf(baselineFiles);
    }

    public Path root() {
        return root;
    }

    public String baseline() {
        return baseline;
    }

    /** Files present before any agent touched the workspace (used e.g. to protect applied migrations). */
    public Set<String> baselineFiles() {
        return baselineFiles;
    }

    public synchronized String head() {
        return git("rev-parse", "HEAD").strip();
    }

    public synchronized boolean isClean() {
        return git("status", "--porcelain").isBlank();
    }

    /** Applies whole-file changes. Paths are re-validated here as defense in depth behind the policy gate. */
    public synchronized void apply(List<FileChange> changes) {
        for (FileChange change : changes) {
            Path target = resolveSafely(change.path());
            try {
                switch (change.action()) {
                    case CREATE, MODIFY -> {
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, change.content() == null ? "" : change.content(), StandardCharsets.UTF_8);
                    }
                    case DELETE -> Files.deleteIfExists(target);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot apply change to " + change.path(), e);
            }
        }
    }

    public synchronized String checkpoint(String message) {
        git("add", "-A");
        if (git("status", "--porcelain").isBlank()) {
            return head();
        }
        git("commit", "-q", "-m", message);
        return head();
    }

    public synchronized void resetTo(String commit) {
        git("reset", "-q", "--hard", commit);
        git("clean", "-q", "-fd");
    }

    public synchronized void discardUncommitted() {
        resetTo("HEAD");
    }

    public synchronized String diff(String from, String to) {
        return git("diff", from, to);
    }

    public synchronized List<String> changedFiles(String from, String to) {
        return git("diff", "--name-only", from, to).lines().filter(l -> !l.isBlank()).toList();
    }

    public synchronized String log(String from) {
        return git("log", "--reverse", "--format=%h %s", from + "..HEAD");
    }

    public Optional<String> read(String relativePath) {
        Path path = resolveSafely(relativePath);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + relativePath, e);
        }
    }

    /** Project files relative to the root with forward slashes, excluding build output and git metadata. */
    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith(".git/") && !p.startsWith("target/") && !p.startsWith(".mvn/")
                            && !p.startsWith("mvnw"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list workspace files", e);
        }
    }

    Path resolveSafely(String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("../") || normalized.equals("..") || normalized.startsWith(".git")) {
            throw new SecurityException("Path outside workspace sandbox: " + relativePath);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path outside workspace sandbox: " + relativePath);
        }
        return resolved;
    }

    private String git(String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        ProcessRunner.Result result = processes.run(command, root, GIT_TIMEOUT, Map.of("GIT_TERMINAL_PROMPT", "0"));
        if (!result.ok()) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + result.tail(10));
        }
        return result.output();
    }
}
