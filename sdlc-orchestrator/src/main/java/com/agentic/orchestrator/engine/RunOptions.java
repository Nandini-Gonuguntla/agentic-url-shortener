package com.agentic.orchestrator.engine;

public record RunOptions(ApprovalPolicy approvals) {

    public static RunOptions manual() {
        return new RunOptions(ApprovalPolicy.MANUAL);
    }

    public static RunOptions auto() {
        return new RunOptions(ApprovalPolicy.AUTO);
    }
}
