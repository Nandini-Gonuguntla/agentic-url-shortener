package com.agentic.orchestrator.policy;

/** Ordered by strictness; a report's decision is the strictest of its violations. */
public enum PolicyDecision {
    ALLOW, WARN, REQUIRE_APPROVAL, DENY
}
