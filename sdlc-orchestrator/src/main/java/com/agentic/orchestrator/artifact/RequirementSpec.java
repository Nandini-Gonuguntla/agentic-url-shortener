package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** The normalized engineering problem produced by the requirements stage. */
public record RequirementSpec(
        String title,
        @JsonPropertyDescription("Two or three sentences restating the intent in engineering terms") String summary,
        ChangeType changeType,
        List<String> functionalRequirements,
        List<String> nonFunctionalRequirements,
        @JsonPropertyDescription("Testable criteria with ids AC1, AC2, ...") List<AcceptanceCriterion> acceptanceCriteria,
        List<String> assumptions,
        List<String> outOfScope,
        @JsonPropertyDescription("Open questions. Mark blocking=true only when a wrong guess would change the design")
        List<Ambiguity> ambiguities) {

    public record AcceptanceCriterion(String id, String description) {
    }

    public record Ambiguity(
            String id,
            String question,
            List<String> options,
            String recommendedOption,
            boolean blocking,
            @JsonPropertyDescription("What changes depending on the answer") String impact) {
    }

    public List<Ambiguity> blockingAmbiguities() {
        return ambiguities == null ? List.of() : ambiguities.stream().filter(Ambiguity::blocking).toList();
    }
}
