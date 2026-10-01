package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** The model's reading of which modules, APIs and data flows a requirement touches. */
public record CodebaseImpact(
        String summary,
        List<ImpactedFile> impactedFiles,
        @JsonPropertyDescription("HTTP endpoints added or changed, for example GET /{code}") List<String> impactedApis,
        @JsonPropertyDescription("Request and data flows affected, described end to end") List<String> dataFlows,
        List<String> architecturalNotes) {

    public record ImpactedFile(String path, ChangeAction changeKind, String reason) {
    }
}
