package com.agentic.orchestrator.workflow;

public class InvalidWorkflowException extends RuntimeException {

    public InvalidWorkflowException(String message) {
        super(message);
    }
}
