package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record TaskPlan(String rationale, List<PlannedTask> tasks) {

    public record PlannedTask(
            @JsonPropertyDescription("T1, T2, ...") String id,
            String title,
            String description,
            TaskKind kind,
            List<String> dependsOn,
            @JsonPropertyDescription("Files this task creates or modifies") List<String> files,
            @JsonPropertyDescription("Acceptance criterion ids this task satisfies") List<String> coversCriteria) {
    }
}
