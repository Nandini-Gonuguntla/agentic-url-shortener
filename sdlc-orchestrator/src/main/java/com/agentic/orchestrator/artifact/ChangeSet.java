package com.agentic.orchestrator.artifact;

import java.util.List;

/** Proposed workspace changes returned by an agent. Applied by the engine only after policy gates pass. */
public record ChangeSet(List<TaskChangeSet> groups) {

    public List<FileChange> allChanges() {
        return groups.stream().flatMap(g -> g.changes().stream()).toList();
    }

    public boolean isEmpty() {
        return allChanges().isEmpty();
    }
}
