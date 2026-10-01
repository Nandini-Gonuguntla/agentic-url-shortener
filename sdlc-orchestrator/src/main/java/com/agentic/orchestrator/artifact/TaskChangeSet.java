package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** The changes produced for one planned task; the engine commits each one as its own checkpoint. */
public record TaskChangeSet(
        String taskId,
        @JsonPropertyDescription("One-line summary used as the checkpoint commit message") String summary,
        List<FileChange> changes,
        @JsonPropertyDescription("Anything a reviewer should know about this change") String notes) {
}
