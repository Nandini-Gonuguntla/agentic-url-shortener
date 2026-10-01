package com.agentic.orchestrator.engine;

public enum StageStatus {
    PENDING, RUNNING, AWAITING_APPROVAL, AWAITING_INPUT, SUCCEEDED, FAILED, CANCELLED;

    public boolean awaitingHuman() {
        return this == AWAITING_APPROVAL || this == AWAITING_INPUT;
    }
}
