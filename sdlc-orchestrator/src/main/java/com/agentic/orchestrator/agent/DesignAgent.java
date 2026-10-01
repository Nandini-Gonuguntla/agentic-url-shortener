package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DesignAgent extends LlmAgent<DesignDoc> {

    public DesignAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "design";
    }

    @Override
    protected Class<DesignDoc> outputType() {
        return DesignDoc.class;
    }

    @Override
    protected String instructions() {
        return """
                Role: software architect.
                Produce a design that fits the existing architecture (controller -> service -> repository,
                RFC 9457 problem details, Flyway migrations, cache on the redirect path).
                - Name components with responsibilities and the files they live in.
                - List API and schema changes explicitly; schema changes must be additive new Flyway migrations.
                - Classify impacts honestly: SCHEMA_CHANGE, PUBLIC_API_CHANGE, SECURITY_SENSITIVE, CONFIG_CHANGE,
                  DEPENDENCY_CHANGE, DATA_PRIVACY. These drive approvals and extra review stages.
                - Record each key decision with its rationale and the alternatives you rejected and why.
                - Give risks with likelihood, impact and mitigation, a test strategy, and a rollback plan.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        RequirementSpec spec = ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class);
        CodebaseAnalysis analysis = ctx.require(WorkflowTemplates.ANALYSIS, CodebaseAnalysis.class);
        return section("Requirement", render(spec))
                + section("Impact analysis", render(analysis.impact()))
                + section("Existing endpoints", render(analysis.inventory().endpoints()))
                + section("Impacted source", FileContext.render(analysis.impact().impactedFiles().stream()
                .map(f -> f.path()).toList(), ctx.workspace()));
    }
}
