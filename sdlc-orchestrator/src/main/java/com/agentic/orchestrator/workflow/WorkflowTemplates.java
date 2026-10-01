package com.agentic.orchestrator.workflow;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * The standard SDLC graph. Conditional stages (threat model, migration safety) are not here:
 * the {@code Replanner} inserts them at run time when the design says they are needed.
 *
 * <pre>
 * requirements -> codebase-analysis -> design -> plan -> implementation -+-> validation -------+
 *                                                                        +-> security-review --+-> release-readiness -> publish
 *                                                                        +-> documentation ----+
 * </pre>
 */
public final class WorkflowTemplates {

    public static final String REQUIREMENTS = "requirements";
    public static final String ANALYSIS = "codebase-analysis";
    public static final String DESIGN = "design";
    public static final String THREAT_MODEL = "threat-model";
    public static final String PLAN = "plan";
    public static final String IMPLEMENTATION = "implementation";
    public static final String VALIDATION = "validation";
    public static final String SECURITY_REVIEW = "security-review";
    public static final String MIGRATION_SAFETY = "migration-safety";
    public static final String DOCUMENTATION = "documentation";
    public static final String RELEASE_READINESS = "release-readiness";
    public static final String PUBLISH = "publish";

    /** Product code the implementation agent may touch; everything else is out of bounds. */
    static final String[] IMPLEMENTATION_PATHS = {
            "src/main/java/**", "src/test/java/**", "src/main/resources/**", "src/test/resources/**", "pom.xml"};
    static final String[] DOCUMENTATION_PATHS = {"docs/**", "README.md", "CHANGELOG.md"};

    private WorkflowTemplates() {
    }

    public static WorkflowGraph standardSdlc() {
        return new WorkflowGraph(List.of(
                StageDefinition.builder(REQUIREMENTS, "requirements")
                        .description("Interpret intent, surface ambiguity, normalize into testable criteria")
                        .exitGates("requirements-clarity")
                        .build(),
                StageDefinition.builder(ANALYSIS, "codebase-analysis")
                        .description("Inventory the codebase and identify impacted modules, APIs and data flows")
                        .dependsOn(REQUIREMENTS)
                        .exitGates("analysis-complete")
                        .fallbackAgent("codebase-analysis-static")
                        .build(),
                StageDefinition.builder(DESIGN, "design")
                        .description("Architecture and design decisions, impacts, risks, rollback plan")
                        .dependsOn(REQUIREMENTS, ANALYSIS)
                        .exitGates("design-complete")
                        .approval(ApprovalMode.RISK_BASED)
                        .build(),
                StageDefinition.builder(PLAN, "planning")
                        .description("Decompose into dependency-ordered tasks covering every acceptance criterion")
                        .dependsOn(DESIGN)
                        .exitGates("plan-valid")
                        .build(),
                StageDefinition.builder(IMPLEMENTATION, "implementation")
                        .description("Execute plan tasks, producing code and tests as policy-checked change sets")
                        .dependsOn(PLAN)
                        .entryGates("workspace-clean")
                        .exitGates("change-policy", "tests-included")
                        .timeout(Duration.ofMinutes(20))
                        .allowedPaths(IMPLEMENTATION_PATHS)
                        .build(),
                StageDefinition.builder(VALIDATION, "validation")
                        .description("Compile and run the full test suite in the isolated workspace")
                        .dependsOn(IMPLEMENTATION)
                        .exitGates("tests-pass")
                        .reworkTarget(IMPLEMENTATION)
                        .maxAttempts(1)
                        .timeout(Duration.ofMinutes(15))
                        .build(),
                StageDefinition.builder(SECURITY_REVIEW, "security-review")
                        .description("Scan the cumulative diff for secrets, dangerous APIs and privacy issues")
                        .dependsOn(IMPLEMENTATION)
                        .exitGates("no-high-findings")
                        .reworkTarget(IMPLEMENTATION)
                        .maxAttempts(1)
                        .build(),
                StageDefinition.builder(DOCUMENTATION, "documentation")
                        .description("Update docs and changelog for the change")
                        .dependsOn(IMPLEMENTATION)
                        .entryGates("workspace-clean")
                        .exitGates("change-policy", "docs-present")
                        .fallbackAgent("documentation-template")
                        .allowedPaths(DOCUMENTATION_PATHS)
                        .build(),
                StageDefinition.builder(RELEASE_READINESS, "release-readiness")
                        .description("Aggregate evidence into a go/no-go checklist; human sign-off required")
                        .dependsOn(VALIDATION, SECURITY_REVIEW, DOCUMENTATION)
                        .exitGates("release-checklist")
                        .approval(ApprovalMode.ALWAYS)
                        .reworkTarget(IMPLEMENTATION)
                        .maxAttempts(1)
                        .build(),
                StageDefinition.builder(PUBLISH, "publish")
                        .description("Produce the reviewable bundle: patch, PR description, engineering summary")
                        .dependsOn(RELEASE_READINESS)
                        .maxAttempts(2)
                        .build()));
    }

    public static StageDefinition threatModelStage() {
        return StageDefinition.builder(THREAT_MODEL, "threat-model")
                .description("STRIDE threat model for a security-sensitive change (inserted by re-planning)")
                .dependsOn(DESIGN)
                .exitGates("threat-model-complete")
                .build();
    }

    public static StageDefinition migrationSafetyStage() {
        return StageDefinition.builder(MIGRATION_SAFETY, "migration-safety")
                .description("Verify schema migrations are additive and backward compatible (inserted by re-planning)")
                .dependsOn(IMPLEMENTATION)
                .exitGates("migration-safe")
                .reworkTarget(IMPLEMENTATION)
                .maxAttempts(1)
                .build();
    }

    public static Set<String> threatModelDownstream() {
        return Set.of(PLAN);
    }

    public static Set<String> migrationSafetyDownstream() {
        return Set.of(RELEASE_READINESS);
    }
}
