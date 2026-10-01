package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.ChangeSet;

/** What an agent hands back. Side effects (workspace writes) are proposals in {@code changes}. */
public record StageOutcome(boolean success, Object artifact, ChangeSet changes, String error, String producedBy) {

    public static StageOutcome success(Object artifact, String producedBy) {
        return new StageOutcome(true, artifact, null, null, producedBy);
    }

    public static StageOutcome success(Object artifact, ChangeSet changes, String producedBy) {
        return new StageOutcome(true, artifact, changes, null, producedBy);
    }

    public static StageOutcome failure(String error) {
        return new StageOutcome(false, null, null, error, null);
    }

    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }
}
