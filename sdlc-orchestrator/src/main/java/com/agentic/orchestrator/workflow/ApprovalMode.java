package com.agentic.orchestrator.workflow;

/** When a stage needs a human sign-off after its exit gates pass. */
public enum ApprovalMode {
    /** Never by stage configuration (policy gates can still demand approval). */
    NEVER,
    /** Always: the stage gates a high-impact action. */
    ALWAYS,
    /** Only when the assessed run risk reaches the configured threshold. */
    RISK_BASED
}
