package com.agentic.orchestrator.policy;

import java.util.List;

public record PolicyReport(String scope, PolicyDecision decision, List<PolicyViolation> violations, int filesChanged) {

    public boolean denied() {
        return decision == PolicyDecision.DENY;
    }

    public boolean requiresApproval() {
        return decision == PolicyDecision.REQUIRE_APPROVAL;
    }

    public List<PolicyViolation> withDecision(PolicyDecision value) {
        return violations.stream().filter(v -> v.decision() == value).toList();
    }
}
