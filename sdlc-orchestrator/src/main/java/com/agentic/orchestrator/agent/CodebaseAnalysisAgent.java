package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.CodebaseImpact;
import com.agentic.orchestrator.artifact.RepoInventory;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.llm.LlmResponse;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.agentic.orchestrator.workspace.CodebaseScanner;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Brownfield reasoning in two steps: a deterministic scan collects facts (files, endpoints,
 * entities, migrations, keyword hits), then the model judges impact from those facts and the
 * relevant source. Facts and judgement are stored separately in the artifact.
 */
public class CodebaseAnalysisAgent extends LlmAgent<CodebaseImpact> {

    private final CodebaseScanner scanner;

    public CodebaseAnalysisAgent(ObjectMapper json, CodebaseScanner scanner) {
        super(json);
        this.scanner = scanner;
    }

    @Override
    public String name() {
        return "codebase-analysis";
    }

    @Override
    protected Class<CodebaseImpact> outputType() {
        return CodebaseImpact.class;
    }

    @Override
    protected String instructions() {
        return """
                Role: senior engineer doing impact analysis on an existing codebase.
                Using the inventory and source provided, identify exactly which files must be created or modified,
                which HTTP APIs change, which request/data flows are affected, and architectural constraints to respect
                (layering, transactions, caching, error handling). Every MODIFY path must exist in the inventory.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        throw new UnsupportedOperationException("built in execute");
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        RequirementSpec spec = ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class);
        RepoInventory inventory = scanner.scan(ctx.workspace(), ctx.scenario().requirement() + " " + spec.summary());
        ctx.telemetry().event(name(), "TOOL_CALL", java.util.Map.of("tool", "codebase-scanner",
                "files", inventory.totalFiles(), "endpoints", inventory.endpoints().size(),
                "keywordHits", inventory.keywordHits().size()));
        List<String> relevant = new ArrayList<>();
        inventory.keywordHits().forEach(hit -> relevant.add(hit.substring(0, hit.indexOf(' '))));
        relevant.addAll(inventory.configFiles());
        relevant.addAll(inventory.migrations());
        String prompt = section("Requirement", render(spec))
                + section("Inventory (facts from static scan)", render(inventory))
                + section("Relevant source", FileContext.render(relevant, ctx.workspace()));
        LlmResponse<CodebaseImpact> response = call(ctx, replayKey(ctx), prompt, CodebaseImpact.class);
        return StageOutcome.success(new CodebaseAnalysis(inventory, response.value(), false), producedBy(response));
    }
}
