package com.agentic.orchestrator.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves repository paths so the orchestrator works from the repo root, the module dir, or a jar. */
public record ProjectLayout(Path repoRoot, Path targetProject, Path scenariosDir, Path runsDir) {

    public static ProjectLayout resolve(OrchestratorProperties props) {
        Path root = blank(props.repoRoot()) ? detectRoot() : Path.of(props.repoRoot()).toAbsolutePath().normalize();
        return new ProjectLayout(root,
                pathOr(props.targetProject(), root.resolve("shortener-service")),
                pathOr(props.scenariosDir(), root.resolve("sdlc-orchestrator/scenarios")),
                pathOr(props.runsDir(), root.resolve("runs")));
    }

    private static Path detectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("shortener-service/pom.xml"))
                    && Files.isDirectory(candidate.resolve("sdlc-orchestrator"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate the repository root from " + dir
                + "; set orchestrator.repo-root");
    }

    private static Path pathOr(String value, Path fallback) {
        return blank(value) ? fallback : Path.of(value).toAbsolutePath().normalize();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
