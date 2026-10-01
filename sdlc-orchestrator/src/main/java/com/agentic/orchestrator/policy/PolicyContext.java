package com.agentic.orchestrator.policy;

import java.util.List;
import java.util.Set;

/** What a change is checked against: the stage's write boundary and the workspace baseline. */
public record PolicyContext(String scope, List<String> allowedPaths, Set<String> baselineFiles, int maxFiles) {
}
