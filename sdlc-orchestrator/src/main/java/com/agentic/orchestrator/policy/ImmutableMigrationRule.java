package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.Severity;

import java.util.List;

/** Applied migrations are history: editing one desynchronizes every database that already ran it. */
public class ImmutableMigrationRule implements PolicyRule {

    @Override
    public String id() {
        return "immutable-migrations";
    }

    @Override
    public List<PolicyViolation> check(FileChange change, PolicyContext context) {
        if (change.path().contains("db/migration/") && change.action() != ChangeAction.CREATE
                && context.baselineFiles().contains(change.path())) {
            return List.of(new PolicyViolation(id(), PolicyDecision.DENY, Severity.CRITICAL, change.path(), 0,
                    "Existing migrations must never be modified or deleted; add a new versioned migration instead"));
        }
        return List.of();
    }
}
