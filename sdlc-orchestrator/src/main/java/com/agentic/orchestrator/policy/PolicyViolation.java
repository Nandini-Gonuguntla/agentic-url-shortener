package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.Severity;

public record PolicyViolation(String rule, PolicyDecision decision, Severity severity, String path, int line,
                              String message) {
}
