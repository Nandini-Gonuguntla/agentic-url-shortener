package com.agentic.orchestrator.engine;

/**
 * Why a stage failed. Only quality failures (gates) trigger rework of an upstream stage;
 * infrastructure failures retry in place, because rewriting code will not fix a broken tool.
 */
public enum FailureKind {
    AGENT_ERROR, GATE_FAILED, ENTRY_GATE_FAILED, TIMEOUT, REJECTED
}
