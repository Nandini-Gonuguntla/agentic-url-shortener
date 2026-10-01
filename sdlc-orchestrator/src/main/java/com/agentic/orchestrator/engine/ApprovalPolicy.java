package com.agentic.orchestrator.engine;

/** How human checkpoints are satisfied for a run. */
public enum ApprovalPolicy {
    /** Wait for a person (REST API, dashboard or interactive CLI). */
    MANUAL,
    /** Demo/CI mode: decisions are made automatically but still recorded as distinct, attributable audit events. */
    AUTO
}
