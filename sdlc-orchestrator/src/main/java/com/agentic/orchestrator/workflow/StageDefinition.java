package com.agentic.orchestrator.workflow;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A node in the workflow graph. Immutable: graph mutations replace definitions.
 *
 * @param reworkTarget  upstream stage to send back for rework when this stage's exit gates fail
 *                      (e.g. failing tests send work back to implementation); null means retry in place
 * @param allowedPaths  write boundary (globs) for stages that produce workspace changes
 */
public record StageDefinition(
        String id,
        String agent,
        String description,
        Set<String> dependsOn,
        List<String> entryGates,
        List<String> exitGates,
        ApprovalMode approval,
        int maxAttempts,
        String fallbackAgent,
        String reworkTarget,
        Duration timeout,
        List<String> allowedPaths) {

    public StageDefinition {
        dependsOn = Set.copyOf(new LinkedHashSet<>(dependsOn));
        entryGates = List.copyOf(entryGates);
        exitGates = List.copyOf(exitGates);
        allowedPaths = List.copyOf(allowedPaths);
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1 for stage " + id);
        }
    }

    public boolean writesWorkspace() {
        return !allowedPaths.isEmpty();
    }

    public StageDefinition withDependency(String stageId) {
        Set<String> deps = new LinkedHashSet<>(dependsOn);
        deps.add(stageId);
        return new StageDefinition(id, agent, description, deps, entryGates, exitGates, approval, maxAttempts,
                fallbackAgent, reworkTarget, timeout, allowedPaths);
    }

    public static Builder builder(String id, String agent) {
        return new Builder(id, agent);
    }

    public static final class Builder {
        private final String id;
        private final String agent;
        private String description = "";
        private Set<String> dependsOn = Set.of();
        private List<String> entryGates = List.of();
        private List<String> exitGates = List.of();
        private ApprovalMode approval = ApprovalMode.NEVER;
        private int maxAttempts = 2;
        private String fallbackAgent;
        private String reworkTarget;
        private Duration timeout = Duration.ofMinutes(10);
        private List<String> allowedPaths = List.of();

        private Builder(String id, String agent) {
            this.id = id;
            this.agent = agent;
        }

        public Builder description(String value) { this.description = value; return this; }
        public Builder dependsOn(String... ids) { this.dependsOn = new LinkedHashSet<>(List.of(ids)); return this; }
        public Builder entryGates(String... gates) { this.entryGates = List.of(gates); return this; }
        public Builder exitGates(String... gates) { this.exitGates = List.of(gates); return this; }
        public Builder approval(ApprovalMode value) { this.approval = value; return this; }
        public Builder maxAttempts(int value) { this.maxAttempts = value; return this; }
        public Builder fallbackAgent(String value) { this.fallbackAgent = value; return this; }
        public Builder reworkTarget(String value) { this.reworkTarget = value; return this; }
        public Builder timeout(Duration value) { this.timeout = value; return this; }
        public Builder allowedPaths(String... globs) { this.allowedPaths = List.of(globs); return this; }

        public StageDefinition build() {
            return new StageDefinition(id, agent, description, dependsOn, entryGates, exitGates, approval,
                    maxAttempts, fallbackAgent, reworkTarget, timeout, allowedPaths);
        }
    }
}
