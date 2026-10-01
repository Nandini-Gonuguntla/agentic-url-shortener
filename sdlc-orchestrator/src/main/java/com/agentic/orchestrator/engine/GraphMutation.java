package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.workflow.StageDefinition;

import java.util.Set;

public record GraphMutation(StageDefinition stage, Set<String> downstream, String reason) {
}
