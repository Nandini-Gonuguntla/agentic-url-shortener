package com.agentic.orchestrator.artifact;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record DesignDoc(
        String overview,
        List<Component> components,
        List<ApiChange> apiChanges,
        List<String> schemaChanges,
        @JsonPropertyDescription("Impact classes; these drive approvals and extra workflow stages")
        List<ImpactFlag> impacts,
        List<Decision> decisions,
        List<Alternative> alternatives,
        List<Risk> risks,
        String testStrategy,
        String rollbackPlan) {

    public record Component(String name, String responsibility, List<String> files) {
    }

    public record ApiChange(String method, String path, String description, boolean breaking) {
    }

    public record Decision(String decision, String rationale) {
    }

    public record Alternative(String option, String whyNotChosen) {
    }

    public record Risk(String description, RiskLevel likelihood, RiskLevel impact, String mitigation) {
    }

    public boolean has(ImpactFlag flag) {
        return impacts != null && impacts.contains(flag);
    }
}
