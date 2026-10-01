package com.agentic.orchestrator.llm;

/**
 * Provider fallback chain: the live model first; if it is unavailable after the SDK's own retries
 * (outage, rate limit, refusal, truncation), a recorded response for the same key is used and the
 * response is flagged as degraded so the audit trail shows it.
 */
public class RoutingLlmClient implements LlmClient {

    private final LlmClient primary;
    private final ReplayLlmClient fallback;

    public RoutingLlmClient(LlmClient primary, ReplayLlmClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public <T> LlmResponse<T> generate(LlmRequest request, Class<T> type) {
        try {
            return primary.generate(request, type);
        } catch (LlmException e) {
            if (fallback != null && fallback.hasFixture(request)) {
                return fallback.generate(request, type).asDegraded(e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public String mode() {
        return fallback == null ? primary.mode() : primary.mode() + "+replay-fallback";
    }
}
