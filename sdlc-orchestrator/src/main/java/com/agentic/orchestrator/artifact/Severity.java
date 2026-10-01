package com.agentic.orchestrator.artifact;

public enum Severity {
    INFO, LOW, MEDIUM, HIGH, CRITICAL;

    public boolean atLeast(Severity other) {
        return compareTo(other) >= 0;
    }
}
