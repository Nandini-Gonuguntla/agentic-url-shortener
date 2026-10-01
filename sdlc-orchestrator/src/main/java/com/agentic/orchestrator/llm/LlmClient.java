package com.agentic.orchestrator.llm;

public interface LlmClient {

    /** Returns a schema-conforming instance of {@code type}, or throws {@link LlmException}. */
    <T> LlmResponse<T> generate(LlmRequest request, Class<T> type);

    /** "live", "replay" or "live+replay-fallback": recorded on every run for reproducibility. */
    String mode();
}
