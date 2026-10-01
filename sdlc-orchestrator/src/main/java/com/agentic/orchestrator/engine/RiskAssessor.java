package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.ImpactFlag;
import com.agentic.orchestrator.artifact.RiskLevel;
import com.agentic.orchestrator.policy.PolicyReport;
import com.agentic.orchestrator.workflow.WorkflowTemplates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Transparent additive risk score: every point is traceable to a named factor. */
public class RiskAssessor {

    private static final Map<ImpactFlag, Integer> IMPACT_WEIGHTS = Map.of(
            ImpactFlag.SCHEMA_CHANGE, 3,
            ImpactFlag.SECURITY_SENSITIVE, 3,
            ImpactFlag.DEPENDENCY_CHANGE, 3,
            ImpactFlag.PUBLIC_API_CHANGE, 2,
            ImpactFlag.DATA_PRIVACY, 2,
            ImpactFlag.CONFIG_CHANGE, 1);

    public RiskAssessment assess(RunState run) {
        int score = 0;
        List<String> factors = new ArrayList<>();
        DesignDoc design = run.artifacts().accepted(WorkflowTemplates.DESIGN, DesignDoc.class)
                .or(() -> run.artifacts().latest(WorkflowTemplates.DESIGN)
                        .map(ArtifactVersion::content).filter(DesignDoc.class::isInstance).map(DesignDoc.class::cast))
                .orElse(null);
        if (design != null) {
            for (ImpactFlag flag : design.impacts() == null ? List.<ImpactFlag>of() : design.impacts()) {
                int weight = IMPACT_WEIGHTS.getOrDefault(flag, 1);
                score += weight;
                factors.add(flag + " (+" + weight + ")");
            }
            long highRisks = design.risks() == null ? 0 : design.risks().stream()
                    .filter(r -> r.impact() == RiskLevel.HIGH).count();
            if (highRisks > 0) {
                score += 2 * (int) highRisks;
                factors.add(highRisks + " high-impact design risk(s) (+" + 2 * highRisks + ")");
            }
        }
        for (PolicyReport report : run.policyReports().values()) {
            if (report.requiresApproval()) {
                score += 2;
                factors.add("policy approval required in " + report.scope() + " (+2)");
            }
            if (report.filesChanged() > 10) {
                score += 2;
                factors.add(report.filesChanged() + " files changed in " + report.scope() + " (+2)");
            }
        }
        RiskLevel level = score >= 6 ? RiskLevel.HIGH : score >= 3 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        return new RiskAssessment(score, level, factors);
    }
}
