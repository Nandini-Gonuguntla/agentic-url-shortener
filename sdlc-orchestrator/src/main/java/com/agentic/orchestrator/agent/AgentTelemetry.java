package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.llm.LlmResponse;

import java.util.Map;

/** Lets agents put their model calls and notable events on the run's audit trail. */
public interface AgentTelemetry {

    void llmCall(String agent, LlmResponse<?> response);

    void event(String agent, String type, Map<String, Object> data);
}
