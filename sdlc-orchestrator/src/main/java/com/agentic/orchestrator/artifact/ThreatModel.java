package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ThreatModel(String summary, List<Threat> threats) {

    public record Threat(
            @JsonPropertyDescription("STRIDE category") String category,
            String description,
            String mitigation,
            RiskLevel severity) {
    }
}
