package com.agentic.orchestrator.engine;

public enum RunStatus {
    RUNNING, WAITING_FOR_HUMAN, SUCCEEDED, FAILED, STOPPED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == STOPPED;
    }
}
