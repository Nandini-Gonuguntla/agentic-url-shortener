package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.RequirementSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Interprets intent, detects ambiguity and normalizes the request into a testable spec. */
public class RequirementsAgent extends LlmAgent<RequirementSpec> {

    public RequirementsAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "requirements";
    }

    @Override
    protected Class<RequirementSpec> outputType() {
        return RequirementSpec.class;
    }

    @Override
    protected String replayKey(AgentContext ctx) {
        return ctx.clarifications().isEmpty() ? "requirements" : "requirements.clarified";
    }

    @Override
    protected String instructions() {
        return """
                Role: requirements analyst.
                Turn the stakeholder request into a precise engineering problem statement.
                - Restate the intent; separate functional and non-functional requirements.
                - Write acceptance criteria as observable, testable behaviour with ids AC1, AC2, ...
                - List assumptions you are making and what is out of scope.
                - List ambiguities as questions with 2-4 concrete options and a recommended option.
                  Mark an ambiguity blocking=true only when different answers lead to materially different
                  designs; otherwise state an assumption and keep it non-blocking.
                - If stakeholder clarifications are provided, treat them as decisions: fold them into the
                  requirements and do not ask those questions again.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        StringBuilder prompt = new StringBuilder(section("Stakeholder request", ctx.scenario().requirement()));
        if (!ctx.clarifications().isEmpty()) {
            prompt.append(section("Stakeholder clarifications (decided)",
                    String.join("\n", ctx.clarifications().entrySet().stream()
                            .map(e -> "- " + e.getKey() + ": " + e.getValue()).toList())));
        }
        prompt.append(section("Existing service capabilities", """
                POST /api/v1/links (create, optional customAlias), GET /api/v1/links/{code}, DELETE /api/v1/links/{code},
                GET /api/v1/links/{code}/stats (clicks, unique visitors, per-day, top referrers), GET /{code} (302 redirect).
                Reliability: per-IP rate limit on create, LRU cache on redirects, atomic click counters, RFC 9457 errors."""));
        return prompt.toString();
    }
}
