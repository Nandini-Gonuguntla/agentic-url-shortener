package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.ChangeSet;
import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.RiskLevel;
import com.agentic.orchestrator.artifact.TaskChangeSet;
import com.agentic.orchestrator.gate.GateResult;
import com.agentic.orchestrator.workflow.ApprovalMode;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.agentic.orchestrator.engine.EngineHarness.await;
import static com.agentic.orchestrator.engine.EngineHarness.sleep;
import static com.agentic.orchestrator.engine.EngineHarness.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class RunExecutorTest {

    @TempDir
    Path tmp;

    private EngineHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        harness = new EngineHarness(tmp);
    }

    private static StageDefinition.Builder stage(String id, String agent) {
        return StageDefinition.builder(id, agent).maxAttempts(1);
    }

    private static StageOutcome ok(Object artifact) {
        return StageOutcome.success(artifact, "test");
    }

    private static StageOutcome write(String path, String content) {
        FileChange change = new FileChange(path, ChangeAction.MODIFY, content);
        return StageOutcome.success(content, new ChangeSet(List.of(new TaskChangeSet("T1", "write " + path,
                List.of(change), null))), "test");
    }

    @Test
    void parallelBranchesRunConcurrentlyAndJoinBeforeDownstream() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        List<String> order = new CopyOnWriteArrayList<>();
        java.util.function.Function<com.agentic.orchestrator.agent.AgentContext, StageOutcome> branch = ctx -> {
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
            sleep(400);
            running.decrementAndGet();
            order.add(ctx.stage().id());
            return ok(ctx.stage().id());
        };
        harness.agent("start", ctx -> { order.add("start"); return ok("s"); })
                .agent("branch", branch)
                .agent("join", ctx -> { order.add("join"); return ok("j"); });

        RunState run = harness.start(RunOptions.auto(),
                stage("start", "start").build(),
                stage("left", "branch").dependsOn("start").build(),
                stage("right", "branch").dependsOn("start").build(),
                stage("join", "join").dependsOn("left", "right").build());

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(maxConcurrent.get()).isEqualTo(2);
        assertThat(order.getFirst()).isEqualTo("start");
        assertThat(order.getLast()).isEqualTo("join");
        assertThat(run.audit().verify().valid()).isTrue();
    }

    @Test
    void failedStageIsRetriedWithFeedbackWithinItsAttemptBudget() throws Exception {
        List<List<String>> feedbackSeen = new CopyOnWriteArrayList<>();
        harness.agent("flaky", ctx -> {
            feedbackSeen.add(ctx.feedback());
            return ctx.execution() == 1 ? StageOutcome.failure("transient outage") : ok("done");
        });

        RunState run = harness.start(RunOptions.auto(), stage("work", "flaky").maxAttempts(2).build());

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.snapshot().counters()).containsEntry("retries", 1L);
        assertThat(feedbackSeen.get(0)).isEmpty();
        assertThat(feedbackSeen.get(1)).singleElement().asString().contains("transient outage");
        assertThat(harness.metrics.snapshot().counters()).containsEntry("stage.recoveries", 1L);
    }

    @Test
    void fallbackAgentTakesOverWhenPrimaryExhaustsRetries() throws Exception {
        harness.agent("primary", ctx -> StageOutcome.failure("model unavailable"))
                .agent("backup", ctx -> ok("from backup"));

        RunState run = harness.start(RunOptions.auto(), stage("work", "primary").fallbackAgent("backup").build());

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.snapshot().stages().getFirst().usingFallback()).isTrue();
        assertThat(run.artifacts().latestAccepted("work").orElseThrow().content()).isEqualTo("from backup");
        assertThat(run.snapshot().counters()).containsEntry("fallbacks", 1L);
    }

    @Test
    void qualityGateFailureRollsBackAndReworksUpstreamStage() throws Exception {
        harness.gates.register("must-be-good", ctx -> "good".equals(ctx.outcome().artifact())
                ? GateResult.pass("must-be-good") : GateResult.fail("must-be-good", "artifact is bad"));
        harness.agent("impl", ctx -> write("src/App.txt", "app v" + ctx.execution() + "\n"))
                .agent("check", ctx -> ok(ctx.execution() == 1 ? "bad" : "good"));

        RunState run = harness.start(RunOptions.auto(),
                stage("impl", "impl").allowedPaths("src/**").build(),
                stage("check", "check").dependsOn("impl").exitGates("must-be-good").reworkTarget("impl").build());

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.workspace().read("src/App.txt")).contains("app v2\n");
        // The first attempt's commit was rolled back: only the reworked change is on top of the baseline.
        assertThat(run.workspace().log(run.workspace().baseline()).lines()).hasSize(1);
        assertThat(run.snapshot().counters()).containsEntry("reworks", 1L).containsEntry("rollbacks", 1L);
        assertThat(run.decisions()).extracting(DecisionRecord::type).contains("ROLLBACK", "REWORK");
    }

    @Test
    void reworkBudgetExhaustionFailsTheRunAndRestoresBaseline() throws Exception {
        harness.gates.register("never", ctx -> GateResult.fail("never", "always fails"));
        harness.agent("impl", ctx -> write("src/App.txt", "attempt " + ctx.execution()))
                .agent("check", ctx -> ok("x"));

        RunState run = harness.start(RunOptions.auto(),
                stage("impl", "impl").allowedPaths("src/**").build(),
                stage("check", "check").dependsOn("impl").exitGates("never").reworkTarget("impl").build());

        assertThat(await(run)).isEqualTo(RunStatus.FAILED);
        assertThat(run.statusReason()).contains("Rework budget");
        assertThat(run.workspace().head()).isEqualTo(run.workspace().baseline());
        assertThat(run.outputDir().resolve("failed-attempt.patch")).exists();
    }

    @Test
    void approvalCheckpointPausesUntilAHumanDecides() throws Exception {
        harness.agent("risky", ctx -> ok("plan v" + ctx.execution()));

        RunState run = harness.start(RunOptions.manual(),
                stage("release", "risky").approval(ApprovalMode.ALWAYS).maxAttempts(2).build());

        waitUntil(() -> run.status() == RunStatus.WAITING_FOR_HUMAN);
        String first = run.snapshot().pendingApprovals().getFirst().id();
        harness.engine.decide(run.runId(), first,
                new ApprovalDecision(ApprovalVerdict.REJECT, "alice", "needs a rollback plan", null));

        waitUntil(() -> run.snapshot().pendingApprovals().stream().anyMatch(a -> !a.id().equals(first)));
        String second = run.snapshot().pendingApprovals().getFirst().id();
        harness.engine.decide(run.runId(), second, new ApprovalDecision(ApprovalVerdict.APPROVE, "bob", "ok", null));

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.artifacts().latestAccepted("release").orElseThrow().content()).isEqualTo("plan v2");
        assertThat(run.decisions()).extracting(DecisionRecord::type, DecisionRecord::actor)
                .contains(org.assertj.core.groups.Tuple.tuple("REJECTION", "alice"),
                        org.assertj.core.groups.Tuple.tuple("APPROVAL", "bob"));
    }

    @Test
    void abortDecisionSafeStopsTheRun() throws Exception {
        harness.agent("risky", ctx -> ok("x"));
        RunState run = harness.start(RunOptions.manual(), stage("release", "risky").approval(ApprovalMode.ALWAYS).build());

        waitUntil(() -> run.status() == RunStatus.WAITING_FOR_HUMAN);
        harness.engine.decide(run.runId(), run.snapshot().pendingApprovals().getFirst().id(),
                new ApprovalDecision(ApprovalVerdict.ABORT, "carol", "wrong requirement", null));

        assertThat(await(run)).isEqualTo(RunStatus.STOPPED);
        assertThat(run.statusReason()).contains("carol");
    }

    @Test
    void policyDeniesWritesOutsideTheStageBoundaryAndNothingIsApplied() throws Exception {
        harness.agent("impl", ctx -> write("pom.xml", "<project/>"));

        RunState run = harness.start(RunOptions.auto(),
                stage("impl", "impl").allowedPaths("src/**").exitGates("change-policy").build());

        assertThat(await(run)).isEqualTo(RunStatus.FAILED);
        assertThat(run.statusReason()).contains("autonomy boundary");
        assertThat(run.workspace().read("pom.xml")).isEmpty();
    }

    @Test
    void protectedPathRequiresApprovalBeforeTheChangeIsWritten() throws Exception {
        harness.agent("impl", ctx -> write("src/main/resources/application.yml", "feature: on\n"));

        RunState run = harness.start(RunOptions.manual(),
                stage("impl", "impl").allowedPaths("src/**").exitGates("change-policy").build());

        waitUntil(() -> run.status() == RunStatus.WAITING_FOR_HUMAN);
        assertThat(run.workspace().read("src/main/resources/application.yml")).isEmpty();
        harness.engine.decide(run.runId(), run.snapshot().pendingApprovals().getFirst().id(),
                new ApprovalDecision(ApprovalVerdict.APPROVE, "dave", "config reviewed", null));

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.workspace().read("src/main/resources/application.yml")).contains("feature: on\n");
    }

    @Test
    void safeStopCancelsInFlightAgentsAndDiscardsTheirResults() throws Exception {
        harness.agent("slow", ctx -> {
            sleep(20_000);
            return write("src/App.txt", "too late");
        });
        RunState run = harness.start(RunOptions.auto(), stage("slow", "slow").allowedPaths("src/**").build());

        waitUntil(() -> run.stage("slow").status() == StageStatus.RUNNING);
        harness.engine.stop(run.runId(), "operator kill switch", "erin");

        assertThat(await(run)).isEqualTo(RunStatus.STOPPED);
        assertThat(run.stage("slow").status()).isEqualTo(StageStatus.CANCELLED);
        assertThat(run.workspace().read("src/App.txt")).contains("app v0\n");
    }

    @Test
    void stageTimeoutIsTreatedAsAFailure() throws Exception {
        harness.agent("hang", ctx -> {
            sleep(10_000);
            return ok("never");
        });
        RunState run = harness.start(RunOptions.auto(), stage("hang", "hang").timeout(Duration.ofMillis(500)).build());

        assertThat(await(run)).isEqualTo(RunStatus.FAILED);
        assertThat(run.statusReason()).contains("Timed out");
    }

    @Test
    void exceedingTheAgentInvocationBudgetTriggersSafeStop() throws Exception {
        harness.limits(new AutonomyLimits(2, Duration.ofMinutes(5), 2, Duration.ofMinutes(5), RiskLevel.MEDIUM, 25))
                .agent("broken", ctx -> StageOutcome.failure("still broken"));

        RunState run = harness.start(RunOptions.auto(), stage("work", "broken").maxAttempts(5).build());

        assertThat(await(run)).isEqualTo(RunStatus.STOPPED);
        assertThat(run.statusReason()).contains("Autonomy budget");
    }

    @Test
    void replannerCanInsertAStageThatBlocksDownstreamWork() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        harness.agent("rec", ctx -> { order.add(ctx.stage().id()); return ok(ctx.stage().id()); })
                .replanner(new Replanner() {
                    @Override
                    public List<GraphMutation> afterStageAccepted(String stageId, Object artifact, WorkflowGraph graph) {
                        return stageId.equals("design") && !graph.contains("review")
                                ? List.of(new GraphMutation(stage("review", "rec").dependsOn("design").build(),
                                Set.of("build"), "design needs review"))
                                : List.of();
                    }
                });

        RunState run = harness.start(RunOptions.auto(),
                stage("design", "rec").build(),
                stage("build", "rec").dependsOn("design").build());

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(order).containsExactly("design", "review", "build");
        assertThat(run.audit().events()).extracting(e -> e.type()).contains("GRAPH_MUTATED");
    }

    @Test
    void blockingQuestionsPauseTheStageAndAnswersRegenerateItsArtifact() throws Exception {
        harness.gates.register("clear", ctx -> ctx.run().clarifications().containsKey("Q1")
                ? GateResult.pass("clear")
                : GateResult.needsInput("clear", List.of(new Question("Q1", "req", "Which threat?", List.of("a", "b"),
                "a", "design", java.time.Instant.now()))));
        harness.agent("req", ctx -> ok(ctx.clarifications().isEmpty() ? "draft" : "clarified: " + ctx.clarifications().get("Q1")));

        RunState run = harness.start(RunOptions.manual(), stage("req", "req").exitGates("clear").build());

        waitUntil(() -> !run.snapshot().pendingQuestions().isEmpty());
        harness.engine.answer(run.runId(), "req", java.util.Map.of("Q1", "b"), "pm");

        assertThat(await(run)).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.artifacts().latestAccepted("req").orElseThrow().version()).isEqualTo(2);
        assertThat((String) run.artifacts().latestAccepted("req").orElseThrow().content()).contains("=> b");
    }
}
