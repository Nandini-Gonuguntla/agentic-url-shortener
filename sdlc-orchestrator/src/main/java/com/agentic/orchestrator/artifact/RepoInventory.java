package com.agentic.orchestrator.artifact;

import java.util.List;

/** Deterministic facts about the target codebase, collected by static scanning (no LLM). */
public record RepoInventory(
        int totalFiles,
        List<String> sourceFiles,
        List<String> testFiles,
        List<Endpoint> endpoints,
        List<String> entities,
        List<String> migrations,
        List<String> configFiles,
        List<String> keywordHits) {

    public record Endpoint(String method, String path, String handler) {
    }
}
