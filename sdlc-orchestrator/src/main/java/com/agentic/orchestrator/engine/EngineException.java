package com.agentic.orchestrator.engine;

/** Rejected engine command (unknown run, nothing pending, invalid state). */
public class EngineException extends RuntimeException {

    public enum Reason { NOT_FOUND, CONFLICT, INVALID }

    private final Reason reason;

    public EngineException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
