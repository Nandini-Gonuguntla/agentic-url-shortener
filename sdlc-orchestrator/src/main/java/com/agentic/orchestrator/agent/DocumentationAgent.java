package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.ChangeSet;
import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.DocsUpdate;
import com.agentic.orchestrator.artifact.ImplementationResult;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.TaskChangeSet;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class DocumentationAgent extends LlmAgent<DocsUpdate> {

    public DocumentationAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "documentation";
    }

    @Override
    protected Class<DocsUpdate> outputType() {
        return DocsUpdate.class;
    }

    @Override
    protected String instructions() {
        return """
                Role: technical writer for an engineering team.
                Update documentation for the implemented change: a feature page under docs/ describing behaviour,
                API contract (requests, responses, status codes) and operational notes, plus a CHANGELOG.md entry
                at the top under "Unreleased". Return complete file contents. Only write docs/**, README.md or CHANGELOG.md.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        ImplementationResult implementation = ctx.require(WorkflowTemplates.IMPLEMENTATION, ImplementationResult.class);
        return section("Requirement", render(ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class)))
                + section("Design", render(ctx.require(WorkflowTemplates.DESIGN, DesignDoc.class)))
                + section("Implemented tasks", String.join("\n", implementation.tasks().stream()
                .map(t -> "- " + t.taskId() + ": " + t.summary() + " " + t.changes().stream().map(c -> c.path()).toList())
                .toList()))
                + section("Existing docs", FileContext.render(List.of("CHANGELOG.md", "README.md"), ctx.workspace()));
    }

    @Override
    protected StageOutcome toOutcome(DocsUpdate value, AgentContext ctx, String producedBy) {
        ChangeSet changes = new ChangeSet(List.of(new TaskChangeSet("DOCS", value.summary(), value.changes(), null)));
        return StageOutcome.success(value, changes, producedBy);
    }
}
