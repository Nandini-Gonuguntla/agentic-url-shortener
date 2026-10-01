package com.agentic.orchestrator.artifact;

/** Static inventory plus the model's impact analysis, kept separate so facts and judgement are distinguishable. */
public record CodebaseAnalysis(RepoInventory inventory, CodebaseImpact impact, boolean staticOnly) {
}
