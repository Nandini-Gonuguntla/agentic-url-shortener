package com.agentic.orchestrator.artifact;

import java.util.List;

public record ReleaseReadiness(
        Recommendation recommendation,
        int riskScore,
        RiskLevel riskLevel,
        List<CheckItem> checklist,
        List<String> openRisks,
        String rollbackPlan,
        String summary) {

    public enum Recommendation { GO, NO_GO }

    public record CheckItem(String item, boolean passed, String evidence) {
    }
}
