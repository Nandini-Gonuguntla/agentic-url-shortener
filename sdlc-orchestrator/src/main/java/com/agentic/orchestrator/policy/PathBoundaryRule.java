package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.Severity;

import java.util.List;

/** Autonomy boundary: an agent may only write inside its stage's allowed paths. */
public class PathBoundaryRule implements PolicyRule {

    @Override
    public String id() {
        return "path-boundary";
    }

    @Override
    public List<PolicyViolation> check(FileChange change, PolicyContext context) {
        String path = change.path() == null ? "" : change.path();
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.matches("^[A-Za-z]:.*")
                || path.contains("..") || path.startsWith(".git")) {
            return List.of(new PolicyViolation(id(), PolicyDecision.DENY, Severity.CRITICAL, path, 0,
                    "Path is not a clean relative project path"));
        }
        if (!Globs.matchesAny(path, context.allowedPaths())) {
            return List.of(new PolicyViolation(id(), PolicyDecision.DENY, Severity.HIGH, path, 0,
                    "Outside the autonomy boundary of stage '" + context.scope() + "' (allowed: " + context.allowedPaths() + ")"));
        }
        return List.of();
    }
}
