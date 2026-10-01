package com.agentic.orchestrator.gate;

import com.agentic.orchestrator.engine.AutonomyLimits;
import com.agentic.orchestrator.engine.RunState;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.policy.PolicyEngine;
import com.agentic.orchestrator.workflow.StageDefinition;

/** @param outcome the agent's proposed outcome for exit gates; null for entry gates */
public record GateContext(StageDefinition stage, StageOutcome outcome, RunState run, PolicyEngine policy,
                          AutonomyLimits limits) {

    public <T> T artifact(Class<T> type) {
        if (outcome == null || !type.isInstance(outcome.artifact())) {
            throw new IllegalStateException("Stage " + stage.id() + " did not produce a " + type.getSimpleName());
        }
        return type.cast(outcome.artifact());
    }
}
