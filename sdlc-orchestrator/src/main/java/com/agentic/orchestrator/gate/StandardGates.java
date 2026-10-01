package com.agentic.orchestrator.gate;

import com.agentic.orchestrator.artifact.ChangeSet;
import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.MigrationReport;
import com.agentic.orchestrator.artifact.ReleaseReadiness;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.SecurityReport;
import com.agentic.orchestrator.artifact.Severity;
import com.agentic.orchestrator.artifact.TaskKind;
import com.agentic.orchestrator.artifact.TaskPlan;
import com.agentic.orchestrator.artifact.ThreatModel;
import com.agentic.orchestrator.artifact.ValidationReport;
import com.agentic.orchestrator.engine.Question;
import com.agentic.orchestrator.policy.PolicyContext;
import com.agentic.orchestrator.policy.PolicyDecision;
import com.agentic.orchestrator.policy.PolicyReport;
import com.agentic.orchestrator.policy.PolicyViolation;
import com.agentic.orchestrator.workflow.InvalidWorkflowException;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The built-in entry and exit gates. Each gate is a small, deterministic check over an artifact:
 * agents propose, gates decide.
 */
public final class StandardGates {

    private StandardGates() {
    }

    public static GateRegistry registry() {
        return new GateRegistry()
                .register("workspace-clean", StandardGates::workspaceClean)
                .register("requirements-clarity", StandardGates::requirementsClarity)
                .register("analysis-complete", StandardGates::analysisComplete)
                .register("design-complete", StandardGates::designComplete)
                .register("threat-model-complete", StandardGates::threatModelComplete)
                .register("plan-valid", StandardGates::planValid)
                .register("change-policy", StandardGates::changePolicy)
                .register("tests-included", StandardGates::testsIncluded)
                .register("tests-pass", StandardGates::testsPass)
                .register("no-high-findings", StandardGates::noHighFindings)
                .register("migration-safe", StandardGates::migrationSafe)
                .register("docs-present", StandardGates::docsPresent)
                .register("release-checklist", StandardGates::releaseChecklist);
    }

    static GateResult workspaceClean(GateContext ctx) {
        return ctx.run().workspace().isClean()
                ? GateResult.pass("workspace-clean")
                : GateResult.fail("workspace-clean", "Workspace has uncommitted changes");
    }

    static GateResult requirementsClarity(GateContext ctx) {
        RequirementSpec spec = ctx.artifact(RequirementSpec.class);
        List<String> problems = new ArrayList<>();
        if (spec.acceptanceCriteria() == null || spec.acceptanceCriteria().isEmpty()) {
            problems.add("No acceptance criteria: the requirement is not testable yet");
        }
        if (spec.functionalRequirements() == null || spec.functionalRequirements().isEmpty()) {
            problems.add("No functional requirements extracted");
        }
        if (!problems.isEmpty()) {
            return GateResult.fail("requirements-clarity", problems);
        }
        List<Question> open = spec.blockingAmbiguities().stream()
                .filter(a -> !ctx.run().clarifications().containsKey(a.id()))
                .map(a -> new Question(a.id(), ctx.stage().id(), a.question(), a.options(), a.recommendedOption(),
                        a.impact(), Instant.now()))
                .toList();
        return open.isEmpty() ? GateResult.pass("requirements-clarity") : GateResult.needsInput("requirements-clarity", open);
    }

    static GateResult analysisComplete(GateContext ctx) {
        CodebaseAnalysis analysis = ctx.artifact(CodebaseAnalysis.class);
        if (analysis.inventory().sourceFiles().isEmpty()) {
            return GateResult.fail("analysis-complete", "Inventory found no source files");
        }
        if (analysis.impact().impactedFiles() == null || analysis.impact().impactedFiles().isEmpty()) {
            return GateResult.fail("analysis-complete", "No impacted files identified");
        }
        List<String> unknown = analysis.impact().impactedFiles().stream()
                .filter(f -> f.changeKind() != com.agentic.orchestrator.artifact.ChangeAction.CREATE)
                .map(f -> f.path())
                .filter(p -> !analysis.inventory().sourceFiles().contains(p) && !analysis.inventory().testFiles().contains(p)
                        && !analysis.inventory().configFiles().contains(p) && !analysis.inventory().migrations().contains(p))
                .toList();
        return unknown.isEmpty()
                ? GateResult.pass("analysis-complete")
                : GateResult.fail("analysis-complete", "Impacted files do not exist in the codebase: " + unknown);
    }

    static GateResult designComplete(GateContext ctx) {
        DesignDoc design = ctx.artifact(DesignDoc.class);
        List<String> missing = new ArrayList<>();
        if (blank(design.overview())) missing.add("overview");
        if (design.components() == null || design.components().isEmpty()) missing.add("components");
        if (blank(design.testStrategy())) missing.add("testStrategy");
        if (blank(design.rollbackPlan())) missing.add("rollbackPlan");
        if (design.impacts() == null) missing.add("impacts");
        if (design.decisions() == null || design.decisions().isEmpty()) missing.add("decisions with rationale");
        return missing.isEmpty()
                ? GateResult.pass("design-complete")
                : GateResult.fail("design-complete", "Design is missing: " + String.join(", ", missing));
    }

    static GateResult threatModelComplete(GateContext ctx) {
        ThreatModel model = ctx.artifact(ThreatModel.class);
        if (model.threats() == null || model.threats().isEmpty()) {
            return GateResult.fail("threat-model-complete", "No threats analysed");
        }
        List<String> unmitigated = model.threats().stream().filter(t -> blank(t.mitigation()))
                .map(ThreatModel.Threat::description).toList();
        return unmitigated.isEmpty()
                ? GateResult.pass("threat-model-complete")
                : GateResult.fail("threat-model-complete", "Threats without mitigation: " + unmitigated);
    }

    static GateResult planValid(GateContext ctx) {
        TaskPlan plan = ctx.artifact(TaskPlan.class);
        List<String> problems = new ArrayList<>();
        if (plan.tasks() == null || plan.tasks().isEmpty()) {
            return GateResult.fail("plan-valid", "Plan has no tasks");
        }
        Set<String> ids = new HashSet<>();
        for (TaskPlan.PlannedTask task : plan.tasks()) {
            if (!ids.add(task.id())) {
                problems.add("Duplicate task id " + task.id());
            }
        }
        List<StageDefinition> nodes = new ArrayList<>();
        for (TaskPlan.PlannedTask task : plan.tasks()) {
            List<String> deps = task.dependsOn() == null ? List.of() : task.dependsOn();
            deps.stream().filter(d -> !ids.contains(d)).forEach(d -> problems.add(task.id() + " depends on unknown task " + d));
            nodes.add(StageDefinition.builder(task.id(), "task").dependsOn(deps.stream().filter(ids::contains)
                    .toArray(String[]::new)).build());
        }
        if (problems.isEmpty()) {
            try {
                new WorkflowGraph(nodes);
            } catch (InvalidWorkflowException e) {
                problems.add("Task graph invalid: " + e.getMessage());
            }
        }
        if (plan.tasks().stream().noneMatch(t -> t.kind() == TaskKind.TEST)) {
            problems.add("Plan contains no TEST task");
        }
        RequirementSpec spec = ctx.run().artifacts().accepted("requirements", RequirementSpec.class).orElse(null);
        if (spec != null) {
            Set<String> covered = plan.tasks().stream()
                    .flatMap(t -> t.coversCriteria() == null ? java.util.stream.Stream.<String>empty() : t.coversCriteria().stream())
                    .collect(Collectors.toSet());
            List<String> uncovered = spec.acceptanceCriteria().stream().map(RequirementSpec.AcceptanceCriterion::id)
                    .filter(id -> !covered.contains(id)).toList();
            if (!uncovered.isEmpty()) {
                problems.add("Acceptance criteria not covered by any task: " + uncovered);
            }
        }
        return problems.isEmpty() ? GateResult.pass("plan-valid") : GateResult.fail("plan-valid", problems);
    }

    static GateResult changePolicy(GateContext ctx) {
        ChangeSet changes = ctx.outcome().changes();
        if (changes == null || changes.isEmpty()) {
            return GateResult.fail("change-policy", "Stage produced no changes");
        }
        PolicyContext policyContext = new PolicyContext(ctx.stage().id(), ctx.stage().allowedPaths(),
                ctx.run().workspace().baselineFiles(), ctx.limits().maxFilesPerChange());
        PolicyReport report = ctx.policy().evaluate(policyContext, changes.allChanges());
        ctx.run().policyReports().put(ctx.stage().id(), report);
        List<String> denied = describe(report.withDecision(PolicyDecision.DENY));
        if (!denied.isEmpty()) {
            return GateResult.fail("change-policy", denied);
        }
        List<String> approvals = describe(report.withDecision(PolicyDecision.REQUIRE_APPROVAL));
        if (!approvals.isEmpty()) {
            return GateResult.needsApproval("change-policy", approvals);
        }
        return GateResult.pass("change-policy", describe(report.withDecision(PolicyDecision.WARN)));
    }

    static GateResult testsIncluded(GateContext ctx) {
        ChangeSet changes = ctx.outcome().changes();
        boolean hasTests = changes != null && changes.allChanges().stream()
                .map(FileChange::path).anyMatch(p -> p.startsWith("src/test/"));
        return hasTests
                ? GateResult.pass("tests-included")
                : GateResult.fail("tests-included", "Change set adds or updates no tests");
    }

    static GateResult testsPass(GateContext ctx) {
        ValidationReport report = ctx.artifact(ValidationReport.class);
        if (report.passed()) {
            return GateResult.pass("tests-pass", List.of(report.testsRun() + " tests passed"));
        }
        List<String> reasons = new ArrayList<>();
        reasons.add(report.testsRun() + " run, " + report.failures() + " failed, " + report.errors() + " errors");
        report.failedTests().stream().limit(8)
                .forEach(f -> reasons.add(f.testClass() + "." + f.testName() + ": " + f.message()));
        return GateResult.fail("tests-pass", reasons);
    }

    static GateResult noHighFindings(GateContext ctx) {
        SecurityReport report = ctx.artifact(SecurityReport.class);
        List<String> high = report.findings().stream().filter(f -> f.severity().atLeast(Severity.HIGH))
                .map(f -> f.severity() + " " + f.rule() + " at " + f.path() + ":" + f.line() + " - " + f.message())
                .toList();
        return high.isEmpty() ? GateResult.pass("no-high-findings") : GateResult.fail("no-high-findings", high);
    }

    static GateResult migrationSafe(GateContext ctx) {
        MigrationReport report = ctx.artifact(MigrationReport.class);
        return report.safe()
                ? GateResult.pass("migration-safe", report.migrationsAdded())
                : GateResult.fail("migration-safe", report.findings().stream()
                .map(f -> f.path() + ": " + f.message()).toList());
    }

    static GateResult docsPresent(GateContext ctx) {
        ChangeSet changes = ctx.outcome().changes();
        boolean docs = changes != null && changes.allChanges().stream().map(FileChange::path)
                .anyMatch(p -> p.startsWith("docs/") || p.equals("CHANGELOG.md") || p.equals("README.md"));
        return docs ? GateResult.pass("docs-present") : GateResult.fail("docs-present", "No documentation updated");
    }

    static GateResult releaseChecklist(GateContext ctx) {
        ReleaseReadiness readiness = ctx.artifact(ReleaseReadiness.class);
        if (readiness.recommendation() == ReleaseReadiness.Recommendation.GO) {
            return GateResult.pass("release-checklist");
        }
        return GateResult.fail("release-checklist", readiness.checklist().stream().filter(c -> !c.passed())
                .map(c -> c.item() + ": " + c.evidence()).toList());
    }

    private static List<String> describe(List<PolicyViolation> violations) {
        return violations.stream()
                .map(v -> v.rule() + " [" + v.severity() + "] " + v.path() + (v.line() > 0 ? ":" + v.line() : "") + " " + v.message())
                .toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
