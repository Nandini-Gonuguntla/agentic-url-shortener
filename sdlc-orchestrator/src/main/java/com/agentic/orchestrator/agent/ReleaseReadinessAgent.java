package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.DocsUpdate;
import com.agentic.orchestrator.artifact.ImplementationResult;
import com.agentic.orchestrator.artifact.MigrationReport;
import com.agentic.orchestrator.artifact.ReleaseReadiness;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.RiskLevel;
import com.agentic.orchestrator.artifact.SecurityReport;
import com.agentic.orchestrator.artifact.Severity;
import com.agentic.orchestrator.artifact.TaskPlan;
import com.agentic.orchestrator.artifact.ValidationReport;
import com.agentic.orchestrator.engine.RiskAssessment;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.workflow.WorkflowTemplates;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns the evidence collected by every upstream stage into a go/no-go checklist for the human approver. */
public class ReleaseReadinessAgent implements Agent {

    @Override
    public String name() {
        return "release-readiness";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        RequirementSpec spec = ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class);
        DesignDoc design = ctx.require(WorkflowTemplates.DESIGN, DesignDoc.class);
        TaskPlan plan = ctx.require(WorkflowTemplates.PLAN, TaskPlan.class);
        ImplementationResult implementation = ctx.require(WorkflowTemplates.IMPLEMENTATION, ImplementationResult.class);
        ValidationReport validation = ctx.require(WorkflowTemplates.VALIDATION, ValidationReport.class);
        SecurityReport security = ctx.require(WorkflowTemplates.SECURITY_REVIEW, SecurityReport.class);
        Optional<MigrationReport> migrations = ctx.optional(WorkflowTemplates.MIGRATION_SAFETY, MigrationReport.class);
        Optional<DocsUpdate> docs = ctx.optional(WorkflowTemplates.DOCUMENTATION, DocsUpdate.class);
        AuditLog.Verification audit = ctx.auditCheck().get();

        List<ReleaseReadiness.CheckItem> checklist = new ArrayList<>();
        Set<String> covered = plan.tasks().stream().flatMap(t -> t.coversCriteria().stream()).collect(Collectors.toSet());
        long uncovered = spec.acceptanceCriteria().stream().filter(ac -> !covered.contains(ac.id())).count();
        checklist.add(item("Acceptance criteria traced to tasks", uncovered == 0,
                spec.acceptanceCriteria().size() + " criteria, " + uncovered + " uncovered"));
        checklist.add(item("Full test suite passes", validation.passed(),
                validation.testsRun() + " tests, " + validation.failures() + " failures, " + validation.errors() + " errors"));
        long testFiles = implementation.tasks().stream().flatMap(t -> t.changes().stream())
                .filter(c -> c.path().startsWith("src/test/")).count();
        checklist.add(item("Change ships with tests", testFiles > 0, testFiles + " test file(s) added or updated"));
        long high = security.findings().stream().filter(f -> f.severity().atLeast(Severity.HIGH)).count();
        checklist.add(item("No high/critical security findings", high == 0, security.summary()));
        migrations.ifPresent(m -> checklist.add(item("Schema migrations are additive", m.safe(),
                m.migrationsAdded().isEmpty() ? "no migrations" : String.join(", ", m.migrationsAdded()))));
        checklist.add(item("Documentation updated", docs.isPresent(),
                docs.map(d -> d.changes().size() + " doc file(s)").orElse("missing")));
        checklist.add(item("Rollback plan defined", design.rollbackPlan() != null && !design.rollbackPlan().isBlank(),
                design.rollbackPlan()));
        checklist.add(item("Audit trail intact", audit.valid(), audit.message() + " (" + audit.entries() + " entries)"));

        RiskAssessment risk = ctx.risk().get();
        List<String> openRisks = design.risks() == null ? List.of() : design.risks().stream()
                .filter(r -> r.impact() != RiskLevel.LOW)
                .map(r -> r.description() + " -> " + r.mitigation()).toList();
        boolean go = checklist.stream().allMatch(ReleaseReadiness.CheckItem::passed);
        String summary = (go ? "GO" : "NO-GO") + ": " + checklist.stream().filter(ReleaseReadiness.CheckItem::passed).count()
                + "/" + checklist.size() + " checks passed; risk " + risk.level() + " (score " + risk.score() + ")";
        return StageOutcome.success(new ReleaseReadiness(go ? ReleaseReadiness.Recommendation.GO
                : ReleaseReadiness.Recommendation.NO_GO, risk.score(), risk.level(), checklist, openRisks,
                design.rollbackPlan(), summary), "release-checker");
    }

    private static ReleaseReadiness.CheckItem item(String name, boolean passed, String evidence) {
        return new ReleaseReadiness.CheckItem(name, passed, evidence == null ? "" : evidence);
    }
}
