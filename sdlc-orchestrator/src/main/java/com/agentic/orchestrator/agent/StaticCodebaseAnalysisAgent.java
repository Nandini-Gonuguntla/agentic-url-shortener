package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.CodebaseImpact;
import com.agentic.orchestrator.artifact.RepoInventory;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.workspace.CodebaseScanner;

import java.util.List;

/** Fallback when the model is unavailable: keyword-based impact from the static scan alone, flagged as such. */
public class StaticCodebaseAnalysisAgent implements Agent {

    private final CodebaseScanner scanner;

    public StaticCodebaseAnalysisAgent(CodebaseScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public String name() {
        return "codebase-analysis-static";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        RepoInventory inventory = scanner.scan(ctx.workspace(), ctx.scenario().requirement());
        List<CodebaseImpact.ImpactedFile> files = inventory.keywordHits().stream()
                .map(hit -> new CodebaseImpact.ImpactedFile(hit.substring(0, hit.indexOf(' ')), ChangeAction.MODIFY,
                        "Mentions requirement keywords " + hit.substring(hit.indexOf(' ') + 1)))
                .toList();
        if (files.isEmpty()) {
            files = inventory.endpoints().stream().map(e -> e.handler().substring(0, e.handler().indexOf('.')))
                    .distinct()
                    .flatMap(cls -> inventory.sourceFiles().stream().filter(f -> f.endsWith("/" + cls + ".java")))
                    .map(f -> new CodebaseImpact.ImpactedFile(f, ChangeAction.MODIFY, "API entry point (no keyword hits)"))
                    .toList();
        }
        CodebaseImpact impact = new CodebaseImpact(
                "Static keyword analysis only (model unavailable); review impacted files manually.",
                files, inventory.endpoints().stream().map(e -> e.method() + " " + e.path()).toList(),
                List.of(), List.of("Low-confidence analysis: produced without model reasoning"));
        return StageOutcome.success(new CodebaseAnalysis(inventory, impact, true), "static-scanner");
    }
}
