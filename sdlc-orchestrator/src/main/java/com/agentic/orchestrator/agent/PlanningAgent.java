package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.TaskPlan;
import com.agentic.orchestrator.artifact.ThreatModel;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Task decomposition: an explicit task DAG whose coverage of acceptance criteria is gate-checked. */
public class PlanningAgent extends LlmAgent<TaskPlan> {

    public PlanningAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "planning";
    }

    @Override
    protected Class<TaskPlan> outputType() {
        return TaskPlan.class;
    }

    @Override
    protected String instructions() {
        return """
                Role: tech lead decomposing a design into implementation tasks.
                - Tasks are small, independently reviewable units with ids T1, T2, ... and explicit dependsOn.
                - Each task lists the exact files it creates or modifies and the acceptance criteria it satisfies.
                - Include TEST tasks; every acceptance criterion must be covered by at least one task.
                - Use kind MIGRATION for Flyway scripts and CONFIG for application.yml changes.
                - Do not create DOCS tasks: a separate documentation stage handles docs and changelog.
                Explain the sequencing rationale.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        StringBuilder prompt = new StringBuilder()
                .append(section("Requirement", render(ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class))))
                .append(section("Design", render(ctx.require(WorkflowTemplates.DESIGN, DesignDoc.class))))
                .append(section("Impact analysis",
                        render(ctx.require(WorkflowTemplates.ANALYSIS, CodebaseAnalysis.class).impact())));
        ctx.optional(WorkflowTemplates.THREAT_MODEL, ThreatModel.class)
                .ifPresent(model -> prompt.append(section("Threat model (mitigations must become tasks)", render(model))));
        return prompt.toString();
    }
}
