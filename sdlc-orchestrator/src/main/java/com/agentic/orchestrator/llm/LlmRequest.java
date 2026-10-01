package com.agentic.orchestrator.llm;

/**
 * @param replayKey  stable key for recorded responses, e.g. "implementation.T2"
 * @param execution  how many times this stage has been started in the run (1-based); replay
 *                   fixtures can differ per execution to reproduce retry and rework paths
 */
public record LlmRequest(String agent, String replayKey, int execution, String scenarioId, String system,
                         String prompt, long maxTokens) {
}
