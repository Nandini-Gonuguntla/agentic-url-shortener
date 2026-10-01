package com.agentic.orchestrator.engine;

import java.util.Map;

/** Everything that can change a run's state arrives as an event on the run's queue. */
public sealed interface EngineEvent {

    record StageFinished(String stageId, int generation, int execution, StageOutcome outcome) implements EngineEvent {
    }

    record ApprovalSubmitted(String approvalId, ApprovalDecision decision) implements EngineEvent {
    }

    record AnswersSubmitted(String stageId, Map<String, String> answers, String actor) implements EngineEvent {
    }

    record StopRequested(String reason, String actor) implements EngineEvent {
    }

    record ArtifactOverridden(String stageId, Object content, String actor, String reason) implements EngineEvent {
    }
}
