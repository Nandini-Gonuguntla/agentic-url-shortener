package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One whole-file change. Whole-file content (not diffs) keeps application deterministic. */
public record FileChange(
        @JsonPropertyDescription("Path relative to the project root, using forward slashes") String path,
        ChangeAction action,
        @JsonPropertyDescription("Complete new file content for CREATE/MODIFY; empty for DELETE") String content) {
}
