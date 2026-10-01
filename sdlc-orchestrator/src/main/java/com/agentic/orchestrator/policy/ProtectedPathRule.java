package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.Severity;

import java.util.ArrayList;
import java.util.List;

/** High-impact paths: allowed, but only with a human approval before the change is applied. */
public class ProtectedPathRule implements PolicyRule {

    private record Protected(String glob, String reason) {
    }

    private static final List<Protected> PROTECTED = List.of(
            new Protected("src/main/resources/db/migration/**", "Schema migration (SCHEMA_CHANGE)"),
            new Protected("src/main/resources/application*", "Runtime configuration change (CONFIG_CHANGE)"),
            new Protected("pom.xml", "Build or dependency change (DEPENDENCY_CHANGE)"));

    @Override
    public String id() {
        return "protected-paths";
    }

    @Override
    public List<PolicyViolation> check(FileChange change, PolicyContext context) {
        List<PolicyViolation> violations = new ArrayList<>();
        for (Protected p : PROTECTED) {
            if (Globs.matches(change.path(), p.glob())) {
                violations.add(new PolicyViolation(id(), PolicyDecision.REQUIRE_APPROVAL, Severity.MEDIUM,
                        change.path(), 0, p.reason() + " requires human approval"));
            }
        }
        if (change.action() == com.agentic.orchestrator.artifact.ChangeAction.DELETE && change.path().startsWith("src/main/")) {
            violations.add(new PolicyViolation(id(), PolicyDecision.REQUIRE_APPROVAL, Severity.MEDIUM,
                    change.path(), 0, "Deleting production code requires human approval"));
        }
        return violations;
    }
}
