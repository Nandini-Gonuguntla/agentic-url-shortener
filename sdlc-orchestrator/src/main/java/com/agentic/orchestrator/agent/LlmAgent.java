package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.llm.LlmRequest;
import com.agentic.orchestrator.llm.LlmResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Base for model-backed agents: builds the prompt, calls the model with a structured-output
 * schema, records telemetry. Feedback from failed gates or human reviewers is always included,
 * which is what makes a retry or rework attempt different from the first one.
 */
public abstract class LlmAgent<T> implements Agent {

    protected static final String CONTEXT = """
            You are one agent in a governed software delivery pipeline working on a Java 21 /
            Spring Boot 3.5 URL shortener service (Maven, JPA/Hibernate, Flyway, H2, JUnit 5, AssertJ, MockMvc).
            Your output is checked by deterministic gates and reviewed by humans before anything is applied.
            Be precise and conservative: prefer the smallest change that satisfies the requirement,
            follow the existing code's conventions, and never invent files or APIs that do not exist.
            """;

    protected final ObjectMapper json;

    protected LlmAgent(ObjectMapper json) {
        this.json = json;
    }

    protected abstract Class<T> outputType();

    protected abstract String instructions();

    protected abstract String prompt(AgentContext ctx) throws Exception;

    protected String replayKey(AgentContext ctx) {
        return name();
    }

    protected long maxTokens() {
        return 16_000;
    }

    protected StageOutcome toOutcome(T value, AgentContext ctx, String producedBy) {
        return StageOutcome.success(value, producedBy);
    }

    @Override
    public StageOutcome execute(AgentContext ctx) throws Exception {
        LlmResponse<T> response = call(ctx, replayKey(ctx), prompt(ctx), outputType());
        return toOutcome(response.value(), ctx, producedBy(response));
    }

    protected <R> LlmResponse<R> call(AgentContext ctx, String key, String prompt, Class<R> type) {
        LlmRequest request = new LlmRequest(name(), key, ctx.execution(), ctx.scenario().id(),
                CONTEXT + "\n" + instructions(), prompt + feedbackSection(ctx.feedback()), maxTokens());
        LlmResponse<R> response = ctx.llm().generate(request, type);
        ctx.telemetry().llmCall(name(), response);
        return response;
    }

    protected static String producedBy(LlmResponse<?> response) {
        return response.provider() + ":" + response.model() + (response.degraded() ? " (degraded)" : "");
    }

    protected String render(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    protected static String section(String title, String body) {
        return "\n## " + title + "\n" + body + "\n";
    }

    private static String feedbackSection(List<String> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return "";
        }
        return section("Feedback on your previous attempt (address every point)",
                String.join("\n", feedback.stream().map(f -> "- " + f).toList()));
    }
}
