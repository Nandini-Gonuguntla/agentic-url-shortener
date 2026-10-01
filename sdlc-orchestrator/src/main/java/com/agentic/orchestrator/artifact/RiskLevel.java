package com.agentic.orchestrator.artifact;

public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public boolean atLeast(RiskLevel other) {
        return compareTo(other) >= 0;
    }
}
