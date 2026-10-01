package com.agentic.orchestrator.llm;

/** @param degraded true when the primary provider failed and a fallback served the response */
public record LlmResponse<T>(T value, String provider, String model, String key, long inputTokens, long outputTokens,
                             long latencyMs, boolean degraded, String note) {

    public LlmResponse<T> asDegraded(String reason) {
        return new LlmResponse<>(value, provider, model, key, inputTokens, outputTokens, latencyMs, true, reason);
    }
}
