package com.agentic.orchestrator.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;

import java.time.Duration;

/**
 * Claude via the official Java SDK with structured outputs: the response is constrained to the
 * JSON schema derived from the artifact record, so agents never parse free text.
 * The SDK retries 429/5xx/connection errors with backoff; refusals and truncation surface as
 * {@link LlmException} so the engine's retry/fallback policy can handle them.
 */
public class AnthropicLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;
    private final boolean serverSideFallback;

    public AnthropicLlmClient(String model, boolean serverSideFallback) {
        this.client = AnthropicOkHttpClient.builder()
                .fromEnv()
                .maxRetries(2)
                .timeout(Duration.ofMinutes(10))
                .build();
        this.model = model;
        this.serverSideFallback = serverSideFallback;
    }

    @Override
    public <T> LlmResponse<T> generate(LlmRequest request, Class<T> type) {
        long start = System.nanoTime();
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessage(request.prompt())
                .outputConfig(type);
        if (serverSideFallback) {
            // On a safety-classifier refusal the API re-runs the request on a fallback model in the same call.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> message;
        try {
            message = client.messages().create(builder.build());
        } catch (RuntimeException e) {
            throw new LlmException("Claude request failed for " + request.replayKey() + ": " + e.getMessage(), e);
        }
        String stopReason = message.stopReason().map(Object::toString).orElse("unknown");
        if (stopReason.equalsIgnoreCase("refusal")) {
            throw new LlmException("Claude declined the request for " + request.replayKey());
        }
        if (stopReason.equalsIgnoreCase("max_tokens")) {
            throw new LlmException("Response truncated at max_tokens for " + request.replayKey());
        }
        T value = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new LlmException("No structured output returned for " + request.replayKey()));
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new LlmResponse<>(value, "anthropic", model, request.replayKey(), message.usage().inputTokens(),
                message.usage().outputTokens(), latencyMs, false, null);
    }

    @Override
    public String mode() {
        return "live";
    }
}
