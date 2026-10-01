package com.agentic.orchestrator.scenario;

import java.util.Map;

/**
 * A requirement to run through the workflow.
 *
 * @param scriptedAnswers stakeholder answers used in AUTO mode, keyed by question id; stands in for
 *                        the human in unattended demos
 */
public record ScenarioDefinition(String id, String title, ScenarioType type, String description, String requirement,
                                 Map<String, String> scriptedAnswers) {

    public ScenarioDefinition {
        scriptedAnswers = scriptedAnswers == null ? Map.of() : Map.copyOf(scriptedAnswers);
    }

    public static ScenarioDefinition custom(String title, String requirement) {
        return new ScenarioDefinition("custom", title == null || title.isBlank() ? "Custom requirement" : title,
                ScenarioType.CUSTOM, "Ad-hoc requirement (live mode)", requirement, Map.of());
    }
}
