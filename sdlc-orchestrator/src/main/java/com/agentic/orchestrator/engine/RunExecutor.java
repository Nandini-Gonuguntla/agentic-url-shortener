package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.agent.AgentContext;
import com.agentic.orchestrator.agent.AgentTelemetry;
import com.agentic.orchestrator.artifact.TaskChangeSet;
import com.agentic.orchestrator.gate.GateContext;
import com.agentic.orchestrator.gate.GateResult;
import com.agentic.orchestrator.llm.LlmResponse;
import com.agentic.orchestrator.observability.Ids;
import com.agentic.orchestrator.workflow.ApprovalMode;
import com.agentic.orchestrator.workflow.StageDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The scheduler for one run: a single thread that owns all state transitions.
 *
 * <p>Agents run concurrently on virtual threads, but they never mutate run state or the
 * workspace. They post a {@link EngineEvent.StageFinished} with a proposed outcome, and this
 * thread evaluates gates, asks humans, applies changes and decides what runs next. That makes
 * every transition serial and auditable, and makes cancelling a stale agent harmless.
 */
final class RunExecutor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);
    private static final Duration POLL = Duration.ofMillis(250);

    private final RunState run;
    private final EngineServices svc;
    private final BlockingQueue<EngineEvent> events = new LinkedBlockingQueue<>();
    private final ExecutorService agentPool = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Future<?>> inFlight = new HashMap<>();

    RunExecutor(RunState run, EngineServices svc) {
        this.run = run;
        this.svc = svc;
    }

    void submit(EngineEvent event) {
        events.add(event);
    }

    @Override
    public void run() {
        audit("RUN_STARTED", null, null, "system", Map.of(
                "scenario", run.scenario().id(),
                "approvalPolicy", run.options().approvals().name(),
                "llmMode", svc.llm().mode(),
                "plan", run.graph().topologicalOrder(),
                "autonomy", svc.limits().toString()));
        try {
            while (!run.status().isTerminal()) {
                synchronized (run) {
                    tick();
                }
                if (run.status().isTerminal()) {
                    break;
                }
                EngineEvent event = events.poll(POLL.toMillis(), TimeUnit.MILLISECONDS);
                if (event != null) {
                    synchronized (run) {
                        handle(event);
                        refreshRunStatus();
                    }
                    persistSnapshot();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (run) {
                safeStop("Engine thread interrupted", "system");
            }
        } catch (RuntimeException e) {
            log.error("Engine failure in run {}", run.runId(), e);
            synchronized (run) {
                safeStop("Engine error: " + e, "system");
            }
        } finally {
            agentPool.shutdownNow();
            persistSnapshot();
            run.completion().complete(run.status());
        }
    }

    // ---------------------------------------------------------------- scheduling

    private void tick() {
        enforceBudgets();
        if (run.status().isTerminal()) {
            return;
        }
        checkTimeouts();
        if (run.status().isTerminal()) {
            return;
        }
        boolean started = scheduleReadyStages();
        if (run.status().isTerminal()) {
            return;
        }
        if (allStagesSucceeded()) {
            completeRun();
        } else if (started) {
            refreshRunStatus();
            persistSnapshot();
        }
    }

    private boolean scheduleReadyStages() {
        boolean started = false;
        Instant now = now();
        for (String id : run.graph().topologicalOrder()) {
            if (run.status().isTerminal()) {
                return started;
            }
            StageState st = run.stage(id);
            if (st.status() != StageStatus.PENDING || (st.notBefore() != null && now.isBefore(st.notBefore()))) {
                continue;
            }
            StageDefinition def = run.graph().stage(id);
            boolean depsDone = def.dependsOn().stream().allMatch(d -> run.stage(d).status() == StageStatus.SUCCEEDED);
            if (!depsDone) {
                continue;
            }
            List<GateResult> entry = evaluateGates(def, def.entryGates(), null);
            List<String> failures = reasons(entry, GateResult.Verdict.FAIL);
            if (!failures.isEmpty()) {
                st.lastError(String.join("; ", failures));
                handleFailure(def, st, failures, FailureKind.ENTRY_GATE_FAILED);
                continue;
            }
            start(def, st);
            started = true;
        }
        return started;
    }

    private void start(StageDefinition def, StageState st) {
        String agentName = st.usingFallback() ? def.fallbackAgent() : def.agent();
        Agent agent = svc.agents().get(agentName);
        st.begin(now(), Ids.spanId(), agentName, def.timeout());
        run.agentInvoked();
        AgentContext ctx = new AgentContext(run.runId(), run.scenario(), def, st.executions(),
                st.attemptsInGeneration(), run.feedbackFor(def.id()), Map.copyOf(run.clarifications()),
                run.artifacts(), run.workspace(), svc.llm(), telemetry(def.id(), st.spanId()), run::snapshot,
                () -> svc.risk().assess(run), run.audit()::verify, run.outputDir());
        audit("STAGE_STARTED", def.id(), st.spanId(), "agent:" + agentName, Map.of(
                "execution", st.executions(),
                "attempt", st.attemptsInGeneration(),
                "generation", st.generation(),
                "fallback", st.usingFallback(),
                "feedbackItems", ctx.feedback().size()));
        int generation = st.generation();
        int execution = st.executions();
        inFlight.put(def.id(), agentPool.submit(() -> {
            StageOutcome outcome;
            try {
                outcome = agent.execute(ctx);
                if (outcome == null) {
                    outcome = StageOutcome.failure("Agent " + agentName + " returned no outcome");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outcome = StageOutcome.failure("Agent interrupted");
            } catch (Exception | LinkageError e) {
                log.warn("Agent {} failed in run {}: {}", agentName, run.runId(), e.toString());
                log.debug("Agent failure detail", e);
                outcome = StageOutcome.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            events.add(new EngineEvent.StageFinished(def.id(), generation, execution, outcome));
        }));
    }

    // ---------------------------------------------------------------- event handling

    private void handle(EngineEvent event) {
        if (run.status().isTerminal()) {
            return;
        }
        switch (event) {
            case EngineEvent.StageFinished e -> onStageFinished(e);
            case EngineEvent.ApprovalSubmitted e -> onApproval(e);
            case EngineEvent.AnswersSubmitted e -> onAnswers(e);
            case EngineEvent.StopRequested e -> safeStop(e.reason(), e.actor());
            case EngineEvent.ArtifactOverridden e -> onOverride(e);
        }
    }

    private void onStageFinished(EngineEvent.StageFinished e) {
        StageState st = run.stage(e.stageId());
        if (st.status() != StageStatus.RUNNING || st.generation() != e.generation() || st.executions() != e.execution()) {
            audit("STALE_RESULT_DISCARDED", e.stageId(), null, "system", Map.of(
                    "resultGeneration", e.generation(), "currentGeneration", st.generation(),
                    "reason", "stage was invalidated, cancelled or timed out while the agent ran"));
            return;
        }
        inFlight.remove(e.stageId());
        StageDefinition def = run.graph().stage(e.stageId());
        StageOutcome outcome = e.outcome();
        svc.metrics().stageCompleted(def.id(), Duration.between(st.startedAt(), now()));

        if (!outcome.success()) {
            st.end(now(), "AGENT_ERROR", outcome.error());
            st.lastError(outcome.error());
            handleFailure(def, st, List.of(outcome.error()), FailureKind.AGENT_ERROR);
            return;
        }
        ArtifactVersion version = run.artifacts().put(def.id(), st.generation(), outcome.artifact(),
                outcome.producedBy(), now());
        writeArtifactFile(version);
        audit("ARTIFACT_STORED", def.id(), st.spanId(), "agent:" + st.currentAgent(), Map.of(
                "ref", version.ref(), "hash", version.contentHash(), "producedBy", String.valueOf(outcome.producedBy()),
                "proposedFileChanges", outcome.hasChanges() ? outcome.changes().allChanges().size() : 0));

        List<GateResult> results = evaluateGates(def, def.exitGates(), outcome);
        List<String> failures = reasons(results, GateResult.Verdict.FAIL);
        if (!failures.isEmpty()) {
            version.state(ArtifactVersion.State.REJECTED);
            st.end(now(), "GATE_FAILED", String.join("; ", failures));
            st.lastError(String.join("; ", failures));
            handleFailure(def, st, failures, FailureKind.GATE_FAILED);
            return;
        }
        List<Question> questions = results.stream().flatMap(r -> r.questions().stream()).toList();
        if (!questions.isEmpty()) {
            st.end(now(), "NEEDS_INPUT", questions.size() + " question(s)");
            requestInput(def, st, outcome, questions);
            return;
        }
        List<String> approvalReasons = new ArrayList<>(reasons(results, GateResult.Verdict.NEEDS_APPROVAL));
        approvalReasons.addAll(stageApprovalReasons(def));
        if (!approvalReasons.isEmpty()) {
            st.end(now(), "NEEDS_APPROVAL", String.join("; ", approvalReasons));
            requestApproval(def, st, outcome, version, approvalReasons);
            return;
        }
        st.end(now(), "SUCCEEDED", null);
        accept(def, st, outcome, version, null);
    }

    private List<String> stageApprovalReasons(StageDefinition def) {
        if (def.approval() == ApprovalMode.ALWAYS) {
            return List.of("Stage '" + def.id() + "' gates a high-impact action and always requires sign-off");
        }
        if (def.approval() == ApprovalMode.RISK_BASED) {
            RiskAssessment risk = svc.risk().assess(run);
            if (risk.level().atLeast(svc.limits().approvalRiskThreshold())) {
                return List.of("Assessed risk " + risk.level() + " (score " + risk.score() + "): "
                        + String.join(", ", risk.factors()));
            }
        }
        return List.of();
    }

    private void requestApproval(StageDefinition def, StageState st, StageOutcome outcome, ArtifactVersion version,
                                 List<String> reasons) {
        st.status(StageStatus.AWAITING_APPROVAL);
        st.pendingOutcome(outcome);
        RiskAssessment risk = svc.risk().assess(run);
        ApprovalRequest request = new ApprovalRequest(run.nextApprovalId(), def.id(), st.generation(),
                List.copyOf(reasons), risk.level().name(), risk.score(), version.ref(), now());
        run.pendingApprovals().put(request.id(), request);
        run.count("approvals.requested");
        svc.metrics().increment("approvals.requested");
        audit("APPROVAL_REQUESTED", def.id(), st.spanId(), "system", Map.of(
                "approvalId", request.id(), "reasons", reasons, "risk", risk.level().name(),
                "riskScore", risk.score(), "artifact", version.ref()));
        if (run.options().approvals() == ApprovalPolicy.AUTO) {
            submit(new EngineEvent.ApprovalSubmitted(request.id(), new ApprovalDecision(ApprovalVerdict.APPROVE,
                    "auto-approver", "Auto-approved in demo mode after reviewing: " + String.join("; ", reasons), null)));
        }
    }

    private void requestInput(StageDefinition def, StageState st, StageOutcome outcome, List<Question> questions) {
        st.status(StageStatus.AWAITING_INPUT);
        st.pendingOutcome(outcome);
        run.pendingQuestions().put(def.id(), List.copyOf(questions));
        run.count("questions.asked");
        audit("INPUT_REQUESTED", def.id(), st.spanId(), "system", Map.of(
                "questions", questions.stream().map(q -> q.id() + ": " + q.question()).toList()));
        if (run.options().approvals() == ApprovalPolicy.AUTO) {
            Map<String, String> answers = new LinkedHashMap<>();
            boolean scripted = true;
            for (Question q : questions) {
                String answer = run.scenario().scriptedAnswers().get(q.id());
                if (answer == null) {
                    scripted = false;
                    answer = q.recommendedOption();
                }
                answers.put(q.id(), answer);
            }
            submit(new EngineEvent.AnswersSubmitted(def.id(), answers,
                    scripted ? "stakeholder (scripted for demo)" : "auto-answer (recommended options)"));
        }
    }

    private void onApproval(EngineEvent.ApprovalSubmitted e) {
        ApprovalRequest request = run.pendingApprovals().remove(e.approvalId());
        if (request == null) {
            audit("APPROVAL_IGNORED", null, null, e.decision().approver(), Map.of("approvalId", e.approvalId()));
            return;
        }
        StageState st = run.stage(request.stageId());
        StageDefinition def = run.graph().stage(request.stageId());
        if (st.status() != StageStatus.AWAITING_APPROVAL || st.generation() != request.generation()) {
            audit("APPROVAL_IGNORED", def.id(), null, e.decision().approver(), Map.of(
                    "approvalId", e.approvalId(), "reason", "stage is no longer awaiting this approval"));
            return;
        }
        ApprovalDecision decision = e.decision();
        Duration wait = Duration.between(request.requestedAt(), now());
        svc.metrics().approvalDecided(decision.verdict().name(), wait);
        run.count("approvals." + decision.verdict().name().toLowerCase());
        audit("APPROVAL_DECIDED", def.id(), st.spanId(), "human:" + decision.approver(), Map.of(
                "approvalId", request.id(), "verdict", decision.verdict().name(),
                "comment", String.valueOf(decision.comment()), "waitMs", wait.toMillis()));
        run.decide(now(), def.id(), decision.verdict() == ApprovalVerdict.APPROVE ? "APPROVAL" : "REJECTION",
                decision.approver(), decision.verdict() + " " + def.id(), String.valueOf(decision.comment()),
                List.of(request.artifactRef()));
        ArtifactVersion version = run.artifacts().latest(def.id()).orElseThrow();
        switch (decision.verdict()) {
            case APPROVE -> accept(def, st, st.pendingOutcome(), version, decision.approver());
            case ABORT -> safeStop("Aborted by " + decision.approver() + ": " + decision.comment(), decision.approver());
            case REJECT -> {
                version.state(ArtifactVersion.State.REJECTED);
                st.pendingOutcome(null);
                String feedback = "Rejected by " + decision.approver() + ": " + decision.comment();
                String target = reworkTargetFor(def, decision.reworkStage());
                if (target.equals(def.id())) {
                    run.addFeedback(def.id(), feedback);
                    handleFailure(def, st, List.of(feedback), FailureKind.REJECTED);
                } else if (run.reworkCycles() < svc.limits().maxReworkCycles()) {
                    rework(target, def.id(), List.of(feedback));
                } else {
                    st.status(StageStatus.FAILED);
                    failRun("Rejected at " + def.id() + " and rework budget is exhausted");
                }
            }
        }
    }

    private String reworkTargetFor(StageDefinition def, String requested) {
        if (requested != null && run.graph().contains(requested)
                && run.graph().transitiveDependents(requested).contains(def.id())) {
            return requested;
        }
        return def.reworkTarget() != null ? def.reworkTarget() : def.id();
    }

    private void onAnswers(EngineEvent.AnswersSubmitted e) {
        StageState st = run.stage(e.stageId());
        List<Question> questions = run.pendingQuestions().remove(e.stageId());
        if (questions == null || st.status() != StageStatus.AWAITING_INPUT) {
            audit("INPUT_IGNORED", e.stageId(), null, e.actor(), Map.of("reason", "no questions pending"));
            return;
        }
        String basedOn = run.artifacts().latest(e.stageId()).map(ArtifactVersion::ref).orElse("none");
        for (Question q : questions) {
            String answer = e.answers().getOrDefault(q.id(), q.recommendedOption());
            run.clarifications().put(q.id(), q.question() + " => " + answer);
            run.decide(now(), e.stageId(), "CLARIFICATION", e.actor(), q.id() + ": " + answer, q.question(),
                    List.of(basedOn));
        }
        run.artifacts().supersede(e.stageId());
        st.pendingOutcome(null);
        st.resetAttempts();
        st.status(StageStatus.PENDING);
        audit("INPUT_PROVIDED", e.stageId(), null, "human:" + e.actor(), Map.of(
                "answers", e.answers(), "next", "re-run " + e.stageId() + " with clarifications; downstream re-planned"));
        invalidateDownstream(e.stageId(), "clarified requirements supersede " + basedOn);
    }

    private void onOverride(EngineEvent.ArtifactOverridden e) {
        StageState st = run.stage(e.stageId());
        if (st.status() != StageStatus.SUCCEEDED) {
            audit("OVERRIDE_IGNORED", e.stageId(), null, e.actor(), Map.of("status", st.status().name()));
            return;
        }
        String previous = run.artifacts().latestAccepted(e.stageId()).map(ArtifactVersion::ref).orElse("none");
        ArtifactVersion version = run.artifacts().put(e.stageId(), st.generation(), e.content(), "human:" + e.actor(), now());
        version.state(ArtifactVersion.State.ACCEPTED);
        writeArtifactFile(version);
        run.decide(now(), e.stageId(), "OVERRIDE", e.actor(), "Replaced " + previous + " with " + version.ref(),
                e.reason(), List.of(previous));
        audit("ARTIFACT_OVERRIDDEN", e.stageId(), null, "human:" + e.actor(), Map.of(
                "previous", previous, "new", version.ref(), "reason", String.valueOf(e.reason())));
        invalidateDownstream(e.stageId(), "upstream artifact " + version.ref() + " replaced " + previous);
        applyReplan(run.graph().stage(e.stageId()), e.content());
    }

    // ---------------------------------------------------------------- success, failure, recovery

    private void accept(StageDefinition def, StageState st, StageOutcome outcome, ArtifactVersion version, String approver) {
        if (outcome.hasChanges()) {
            String before = run.workspace().head();
            st.checkpointBefore(before);
            for (TaskChangeSet group : outcome.changes().groups()) {
                if (group.changes().isEmpty()) {
                    continue;
                }
                run.workspace().apply(group.changes());
                String sha = run.workspace().checkpoint("[" + def.id() + "] " + group.taskId() + ": " + group.summary());
                audit("CHECKPOINT", def.id(), st.spanId(), "system", Map.of(
                        "task", group.taskId(), "commit", sha,
                        "files", group.changes().stream().map(c -> c.action() + " " + c.path()).toList()));
            }
        }
        version.state(ArtifactVersion.State.ACCEPTED);
        st.status(StageStatus.SUCCEEDED);
        st.pendingOutcome(null);
        run.clearFeedback(def.id());
        if (st.firstFailureAt() != null) {
            Duration recovery = Duration.between(st.firstFailureAt(), now());
            svc.metrics().recovered(recovery);
            audit("STAGE_RECOVERED", def.id(), st.spanId(), "system", Map.of("timeToRecoverMs", recovery.toMillis()));
            st.firstFailureAt(null);
        }
        audit("STAGE_SUCCEEDED", def.id(), st.spanId(), "agent:" + st.currentAgent(), Map.of(
                "artifact", version.ref(), "approvedBy", approver == null ? "n/a" : approver));
        applyReplan(def, outcome.artifact());
    }

    private void applyReplan(StageDefinition def, Object artifact) {
        for (GraphMutation mutation : svc.replanner().afterStageAccepted(def.id(), artifact, run.graph())) {
            run.graph().insert(mutation.stage(), mutation.downstream());
            run.addStage(mutation.stage());
            run.count("replans");
            run.decide(now(), mutation.stage().id(), "REPLAN", "replanner",
                    "Inserted stage " + mutation.stage().id() + " before " + mutation.downstream(), mutation.reason(),
                    run.artifacts().latestAccepted(def.id()).map(v -> List.of(v.ref())).orElse(List.of()));
            audit("GRAPH_MUTATED", mutation.stage().id(), null, "replanner", Map.of(
                    "inserted", mutation.stage().id(), "dependsOn", List.copyOf(mutation.stage().dependsOn()),
                    "blocks", List.copyOf(mutation.downstream()), "reason", mutation.reason(),
                    "plan", run.graph().topologicalOrder()));
            List<String> alreadyRan = mutation.downstream().stream()
                    .filter(id -> run.stage(id).status() != StageStatus.PENDING).toList();
            for (String id : alreadyRan) {
                List<String> affected = new ArrayList<>(List.of(id));
                affected.addAll(run.graph().transitiveDependents(id));
                rollbackWorkspaceFor(affected, "re-plan inserted " + mutation.stage().id());
                invalidate(affected, "re-plan inserted " + mutation.stage().id() + " upstream");
            }
        }
    }

    private void handleFailure(StageDefinition def, StageState st, List<String> reasons, FailureKind kind) {
        String reason = String.join("; ", reasons);
        if (st.firstFailureAt() == null) {
            st.firstFailureAt(now());
        }
        svc.metrics().increment("stage.failures");
        run.count("stage.failures");
        audit("STAGE_FAILED", def.id(), st.spanId(), "system", Map.of(
                "kind", kind.name(), "reasons", reasons, "execution", st.executions(),
                "attempt", st.attemptsInGeneration()));

        if (kind == FailureKind.ENTRY_GATE_FAILED) {
            // Preconditions are engine invariants (e.g. a clean workspace); retrying cannot fix them.
            st.status(StageStatus.FAILED);
            failRun("Entry gate failed for " + def.id() + ": " + reason);
            return;
        }
        if (kind == FailureKind.GATE_FAILED && def.reworkTarget() != null) {
            if (run.reworkCycles() < svc.limits().maxReworkCycles()) {
                rework(def.reworkTarget(), def.id(), reasons);
            } else {
                st.status(StageStatus.FAILED);
                failRun("Rework budget (" + svc.limits().maxReworkCycles() + ") exhausted; " + def.id()
                        + " still failing: " + reason);
            }
            return;
        }
        if (st.attemptsInGeneration() < def.maxAttempts()) {
            Duration backoff = Duration.ofSeconds(Math.min(10, 1L << (st.attemptsInGeneration() - 1)));
            st.status(StageStatus.PENDING);
            st.notBefore(now().plus(backoff));
            run.addFeedback(def.id(), "Attempt " + st.attemptsInGeneration() + " failed: " + reason);
            run.count("retries");
            svc.metrics().increment("stage.retries");
            run.decide(now(), def.id(), "RETRY", "system", "Retry " + def.id() + " (attempt "
                    + (st.attemptsInGeneration() + 1) + "/" + def.maxAttempts() + ")", reason, List.of());
            audit("RETRY_SCHEDULED", def.id(), st.spanId(), "system", Map.of(
                    "nextAttempt", st.attemptsInGeneration() + 1, "maxAttempts", def.maxAttempts(),
                    "backoffMs", backoff.toMillis()));
            return;
        }
        if (def.fallbackAgent() != null && !st.usingFallback()) {
            st.activateFallback();
            st.status(StageStatus.PENDING);
            run.count("fallbacks");
            svc.metrics().increment("stage.fallbacks");
            run.decide(now(), def.id(), "FALLBACK", "system", "Switch " + def.id() + " to " + def.fallbackAgent(),
                    reason, List.of());
            audit("FALLBACK_ACTIVATED", def.id(), st.spanId(), "system", Map.of(
                    "from", def.agent(), "to", def.fallbackAgent(), "reason", reason));
            return;
        }
        st.status(StageStatus.FAILED);
        failRun("Stage " + def.id() + " failed after " + st.executions() + " execution(s): " + reason);
    }

    /** Sends work back upstream: roll the workspace back, invalidate the affected sub-graph, attach feedback. */
    private void rework(String targetId, String fromStage, List<String> reasons) {
        run.reworkStarted();
        run.count("reworks");
        svc.metrics().increment("stage.reworks");
        List<String> affected = new ArrayList<>(List.of(targetId));
        affected.addAll(run.graph().transitiveDependents(targetId));
        rollbackWorkspaceFor(affected, "rework of " + targetId + " requested by " + fromStage);
        invalidate(affected, "rework requested by " + fromStage);
        run.addFeedback(targetId, "Stage '" + fromStage + "' failed and sent this work back: " + String.join("; ", reasons));
        run.decide(now(), targetId, "REWORK", "system", "Rework " + targetId + " (cycle " + run.reworkCycles() + "/"
                + svc.limits().maxReworkCycles() + ")", String.join("; ", reasons), List.of());
        audit("REWORK_TRIGGERED", targetId, null, "system", Map.of(
                "from", fromStage, "cycle", run.reworkCycles(), "invalidated", affected, "reasons", reasons));
    }

    private void invalidateDownstream(String stageId, String reason) {
        List<String> affected = run.graph().transitiveDependents(stageId).stream()
                .filter(id -> run.stage(id).status() != StageStatus.PENDING || run.stage(id).generation() > 0)
                .toList();
        if (!affected.isEmpty()) {
            rollbackWorkspaceFor(affected, reason);
            invalidate(affected, reason);
        }
    }

    private void invalidate(List<String> stageIds, String reason) {
        for (String id : stageIds) {
            Future<?> future = inFlight.remove(id);
            if (future != null) {
                future.cancel(true);
            }
            run.stage(id).invalidate();
            run.pendingApprovals().values().removeIf(r -> r.stageId().equals(id));
            run.pendingQuestions().remove(id);
            run.artifacts().supersede(id);
        }
        audit("STAGES_INVALIDATED", null, null, "system", Map.of("stages", stageIds, "reason", reason));
    }

    private void rollbackWorkspaceFor(List<String> stageIds, String reason) {
        String target = run.graph().topologicalOrder().stream()
                .filter(stageIds::contains)
                .map(id -> run.stage(id).checkpointBefore())
                .filter(sha -> sha != null)
                .findFirst().orElse(null);
        if (target == null) {
            return;
        }
        String from = run.workspace().head();
        run.workspace().resetTo(target);
        run.count("rollbacks");
        svc.metrics().increment("workspace.rollbacks");
        run.decide(now(), null, "ROLLBACK", "system", "Workspace reset " + abbrev(from) + " -> " + abbrev(target), reason,
                List.of());
        audit("ROLLBACK", null, null, "system", Map.of("from", from, "to", target, "reason", reason));
    }

    // ---------------------------------------------------------------- guardrails

    private void enforceBudgets() {
        if (run.agentInvocations() > svc.limits().maxAgentInvocations()) {
            safeStop("Autonomy budget exceeded: " + run.agentInvocations() + " agent invocations (limit "
                    + svc.limits().maxAgentInvocations() + ")", "guardrail");
            return;
        }
        Duration elapsed = Duration.between(run.createdAt(), now());
        if (elapsed.compareTo(svc.limits().maxRunDuration()) > 0) {
            safeStop("Wall-clock budget exceeded: " + elapsed.toMinutes() + " min", "guardrail");
            return;
        }
        Instant oldestAsk = run.pendingApprovals().values().stream().map(ApprovalRequest::requestedAt)
                .min(Instant::compareTo)
                .or(() -> run.pendingQuestions().values().stream().flatMap(List::stream).map(Question::askedAt)
                        .min(Instant::compareTo))
                .orElse(null);
        if (oldestAsk != null && Duration.between(oldestAsk, now()).compareTo(svc.limits().approvalTimeout()) > 0) {
            safeStop("No human decision within " + svc.limits().approvalTimeout().toMinutes() + " min", "guardrail");
        }
    }

    private void checkTimeouts() {
        Instant now = now();
        for (String id : List.copyOf(inFlight.keySet())) {
            StageState st = run.stage(id);
            if (st.status() == StageStatus.RUNNING && st.deadline() != null && now.isAfter(st.deadline())) {
                inFlight.remove(id).cancel(true);
                StageDefinition def = run.graph().stage(id);
                String reason = "Timed out after " + def.timeout().toMillis() + "ms";
                st.end(now, "TIMEOUT", reason);
                st.lastError(reason);
                handleFailure(def, st, List.of(reason), FailureKind.TIMEOUT);
                if (run.status().isTerminal()) {
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- terminal transitions

    private void completeRun() {
        run.status(RunStatus.SUCCEEDED, "All stages succeeded", now());
        Duration total = Duration.between(run.createdAt(), now());
        svc.metrics().runFinished("SUCCEEDED", total);
        audit("RUN_SUCCEEDED", null, null, "system", Map.of("durationMs", total.toMillis(),
                "agentInvocations", run.agentInvocations(), "counters", run.counters()));
    }

    private void failRun(String reason) {
        cancelEverything();
        String head = run.workspace().head();
        saveAttemptedPatch(head);
        run.workspace().resetTo(run.workspace().baseline());
        run.count("rollbacks");
        svc.metrics().increment("workspace.rollbacks");
        run.decide(now(), null, "ROLLBACK", "system", "Workspace restored to baseline", reason, List.of());
        audit("ROLLBACK", null, null, "system", Map.of("from", head, "to", run.workspace().baseline(),
                "reason", "run failed; no partial change is left behind"));
        run.status(RunStatus.FAILED, reason, now());
        svc.metrics().runFinished("FAILED", Duration.between(run.createdAt(), now()));
        audit("RUN_FAILED", null, null, "system", Map.of("reason", reason));
    }

    /**
     * Safe stop: no new work starts, in-flight agents are cancelled (their results will be
     * discarded), uncommitted workspace changes are dropped, and the workspace stays at its last
     * consistent checkpoint for inspection.
     */
    private void safeStop(String reason, String actor) {
        if (run.status().isTerminal()) {
            return;
        }
        cancelEverything();
        run.workspace().discardUncommitted();
        run.decide(now(), null, "STOP", actor, "Safe stop", reason, List.of());
        audit("SAFE_STOP", null, null, actor, Map.of("reason", reason, "checkpoint", run.workspace().head()));
        run.status(RunStatus.STOPPED, reason, now());
        svc.metrics().runFinished("STOPPED", Duration.between(run.createdAt(), now()));
    }

    private void cancelEverything() {
        inFlight.values().forEach(f -> f.cancel(true));
        inFlight.clear();
        for (StageState st : run.stageStates().values()) {
            if (st.status() == StageStatus.RUNNING || st.status().awaitingHuman()) {
                st.status(StageStatus.CANCELLED);
            }
        }
        run.pendingApprovals().clear();
        run.pendingQuestions().clear();
    }

    // ---------------------------------------------------------------- helpers

    private List<GateResult> evaluateGates(StageDefinition def, List<String> gateNames, StageOutcome outcome) {
        List<GateResult> results = new ArrayList<>();
        GateContext ctx = new GateContext(def, outcome, run, svc.policy(), svc.limits());
        for (String name : gateNames) {
            GateResult result;
            try {
                result = svc.gates().get(name).evaluate(ctx);
            } catch (RuntimeException e) {
                result = GateResult.fail(name, "Gate error: " + e.getMessage());
            }
            results.add(result);
            audit("GATE_EVALUATED", def.id(), run.stage(def.id()).spanId(), "gate:" + name, Map.of(
                    "phase", outcome == null ? "entry" : "exit", "verdict", result.verdict().name(),
                    "reasons", result.reasons()));
        }
        return results;
    }

    private static List<String> reasons(List<GateResult> results, GateResult.Verdict verdict) {
        return results.stream().filter(r -> r.verdict() == verdict)
                .flatMap(r -> r.reasons().stream().map(reason -> r.gate() + ": " + reason)).toList();
    }

    private boolean allStagesSucceeded() {
        return run.stageStates().values().stream().allMatch(s -> s.status() == StageStatus.SUCCEEDED);
    }

    private void refreshRunStatus() {
        if (run.status().isTerminal()) {
            return;
        }
        boolean waiting = run.stageStates().values().stream().anyMatch(s -> s.status().awaitingHuman());
        RunStatus next = waiting ? RunStatus.WAITING_FOR_HUMAN : RunStatus.RUNNING;
        if (next != run.status()) {
            run.status(next, waiting ? "Waiting for a human decision" : "", now());
        }
    }

    private AgentTelemetry telemetry(String stageId, String spanId) {
        return new AgentTelemetry() {
            @Override
            public void llmCall(String agent, LlmResponse<?> response) {
                svc.metrics().increment("llm.calls." + response.provider());
                svc.metrics().add("llm.tokens.input", response.inputTokens());
                svc.metrics().add("llm.tokens.output", response.outputTokens());
                audit("LLM_CALL", stageId, spanId, "agent:" + agent, Map.of(
                        "provider", response.provider(), "model", response.model(), "key", response.key(),
                        "inputTokens", response.inputTokens(), "outputTokens", response.outputTokens(),
                        "latencyMs", response.latencyMs()));
                if (response.degraded()) {
                    run.count("llm.fallbacks");
                    svc.metrics().increment("llm.fallbacks");
                    audit("FALLBACK_USED", stageId, spanId, "agent:" + agent, Map.of(
                            "level", "llm", "provider", response.provider(), "reason", response.note()));
                }
            }

            @Override
            public void event(String agent, String type, Map<String, Object> data) {
                audit(type, stageId, spanId, "agent:" + agent, data);
            }
        };
    }

    private void writeArtifactFile(ArtifactVersion version) {
        try {
            var dir = run.runDir().resolve("artifacts");
            Files.createDirectories(dir);
            svc.json().writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve(version.stageId() + ".v" + version.version() + ".json").toFile(), version.content());
        } catch (IOException e) {
            log.warn("Could not persist artifact {}", version.ref(), e);
        }
    }

    private void saveAttemptedPatch(String head) {
        try {
            Files.createDirectories(run.outputDir());
            Files.writeString(run.outputDir().resolve("failed-attempt.patch"),
                    run.workspace().diff(run.workspace().baseline(), head));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not save failed attempt patch for {}", run.runId(), e);
        }
    }

    private void persistSnapshot() {
        try {
            svc.json().writerWithDefaultPrettyPrinter().writeValue(run.runDir().resolve("state.json").toFile(),
                    run.snapshot());
        } catch (IOException e) {
            log.warn("Could not persist state for {}", run.runId(), e);
        }
    }

    private void audit(String type, String stageId, String spanId, String actor, Map<String, Object> data) {
        run.audit().record(type, stageId, spanId, actor, data);
        log.info("[{}] {} {} {}", run.runId(), type, stageId == null ? "" : stageId, data);
    }

    private Instant now() {
        return svc.clock().instant();
    }

    private static String abbrev(String sha) {
        return sha == null ? "?" : sha.substring(0, Math.min(8, sha.length()));
    }
}
