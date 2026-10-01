package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agent.AgentRegistry;
import com.agentic.orchestrator.gate.GateRegistry;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.observability.ReliabilityMetrics;
import com.agentic.orchestrator.policy.PolicyEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Collaborators shared by every run. */
public record EngineServices(
        AgentRegistry agents,
        GateRegistry gates,
        PolicyEngine policy,
        RiskAssessor risk,
        Replanner replanner,
        ReliabilityMetrics metrics,
        LlmClient llm,
        AutonomyLimits limits,
        ObjectMapper json,
        Clock clock) {
}
