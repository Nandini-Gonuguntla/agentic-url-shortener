package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Evaluates proposed changes against change-control, security and compliance rules before they are applied. */
public class PolicyEngine {

    private final List<PolicyRule> rules;

    public PolicyEngine(List<PolicyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static PolicyEngine standard() {
        return new PolicyEngine(List.of(new PathBoundaryRule(), new ImmutableMigrationRule(), new ProtectedPathRule(),
                new SecretRule(), new DangerousApiRule(), new PiiLoggingRule()));
    }

    public PolicyReport evaluate(PolicyContext context, List<FileChange> changes) {
        List<PolicyViolation> violations = new ArrayList<>();
        for (FileChange change : changes) {
            for (PolicyRule rule : rules) {
                violations.addAll(rule.check(change, context));
            }
        }
        long files = changes.stream().map(FileChange::path).distinct().count();
        if (files > context.maxFiles()) {
            violations.add(new PolicyViolation("change-budget", PolicyDecision.REQUIRE_APPROVAL, Severity.MEDIUM, "*", 0,
                    files + " files changed exceeds the autonomous limit of " + context.maxFiles()));
        }
        PolicyDecision decision = violations.stream().map(PolicyViolation::decision)
                .max(Comparator.naturalOrder()).orElse(PolicyDecision.ALLOW);
        return new PolicyReport(context.scope(), decision, violations, (int) files);
    }

    /** Content-only scan of final file states, used by the security review stage. */
    public List<PolicyViolation> scanContent(String path, String content) {
        List<PolicyViolation> violations = new ArrayList<>();
        for (PolicyRule rule : rules) {
            if (rule instanceof ContentRule contentRule) {
                violations.addAll(contentRule.scan(path, content));
            }
        }
        return violations;
    }
}
