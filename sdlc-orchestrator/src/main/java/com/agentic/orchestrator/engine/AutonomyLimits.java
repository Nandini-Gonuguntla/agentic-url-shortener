package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.RiskLevel;

import java.time.Duration;

/** The boundary inside which agents act without asking. Exceeding any limit triggers a safe stop. */
public record AutonomyLimits(
        int maxAgentInvocations,
        Duration maxRunDuration,
        int maxReworkCycles,
        Duration approvalTimeout,
        RiskLevel approvalRiskThreshold,
        int maxFilesPerChange) {

    public static AutonomyLimits defaults() {
        return new AutonomyLimits(60, Duration.ofMinutes(60), 2, Duration.ofMinutes(30), RiskLevel.MEDIUM, 25);
    }
}
