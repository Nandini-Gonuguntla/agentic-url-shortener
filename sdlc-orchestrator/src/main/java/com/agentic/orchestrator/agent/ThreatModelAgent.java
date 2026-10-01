package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.ThreatModel;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ThreatModelAgent extends LlmAgent<ThreatModel> {

    public ThreatModelAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "threat-model";
    }

    @Override
    protected Class<ThreatModel> outputType() {
        return ThreatModel.class;
    }

    @Override
    protected String instructions() {
        return """
                Role: application security engineer.
                Build a STRIDE threat model for the proposed design. For each credible threat give the category,
                a concrete description, the mitigation the design must include, and severity. Do not pad the list
                with generic threats that do not apply to this change.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        return section("Requirement", render(ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class)))
                + section("Design", render(ctx.require(WorkflowTemplates.DESIGN, DesignDoc.class)));
    }
}
