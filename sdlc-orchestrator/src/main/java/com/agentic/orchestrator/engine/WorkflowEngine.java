package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.observability.Ids;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.agentic.orchestrator.workspace.Workspace;
import com.agentic.orchestrator.workspace.WorkspaceFactory;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Entry point for starting runs and sending them human decisions. One {@link RunExecutor} thread per run. */
public class WorkflowEngine {

    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final EngineServices services;
    private final WorkspaceFactory workspaces;
    private final Path runsDir;
    private final Supplier<WorkflowGraph> graphTemplate;
    private final Map<String, RunState> runs = new ConcurrentHashMap<>();
    private final Map<String, RunExecutor> executors = new ConcurrentHashMap<>();

    public WorkflowEngine(EngineServices services, WorkspaceFactory workspaces, Path runsDir) {
        this(services, workspaces, runsDir, WorkflowTemplates::standardSdlc);
    }

    public WorkflowEngine(EngineServices services, WorkspaceFactory workspaces, Path runsDir,
                          Supplier<WorkflowGraph> graphTemplate) {
        this.services = services;
        this.workspaces = workspaces;
        this.runsDir = runsDir;
        this.graphTemplate = graphTemplate;
    }

    public RunState start(ScenarioDefinition scenario, RunOptions options) {
        String runId = "run-" + RUN_ID_TIME.format(services.clock().instant()) + "-" + Ids.shortId();
        Path runDir = runsDir.resolve(runId);
        try {
            Files.createDirectories(runDir.resolve("output"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create run directory " + runDir, e);
        }
        AuditLog audit = new AuditLog(runId, Ids.traceId(), runDir.resolve("audit.jsonl"), services.clock());
        Workspace workspace = workspaces.create(runDir.resolve("workspace"));
        audit.record("WORKSPACE_CREATED", null, null, "system", Map.of(
                "path", workspace.root().toString(), "baseline", workspace.baseline(),
                "requirement", scenario.requirement()));
        RunState run = new RunState(runId, scenario, options, graphTemplate.get(), new ArtifactStore(services.json()),
                workspace, audit, runDir, services.clock().instant());
        RunExecutor executor = new RunExecutor(run, services);
        runs.put(runId, run);
        executors.put(runId, executor);
        services.metrics().increment("runs.started");
        Thread.ofPlatform().name("run-" + runId).daemon(false).start(executor);
        return run;
    }

    public Optional<RunState> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public RunState get(String runId) {
        return find(runId).orElseThrow(() -> new EngineException(EngineException.Reason.NOT_FOUND, "Unknown run " + runId));
    }

    public List<RunState> list() {
        return runs.values().stream().sorted(Comparator.comparing(RunState::createdAt).reversed()).toList();
    }

    public void decide(String runId, String approvalId, ApprovalDecision decision) {
        RunState run = get(runId);
        synchronized (run) {
            if (!run.pendingApprovals().containsKey(approvalId)) {
                throw new EngineException(EngineException.Reason.CONFLICT, "Approval " + approvalId + " is not pending");
            }
        }
        if (decision.approver() == null || decision.approver().isBlank()) {
            throw new EngineException(EngineException.Reason.INVALID, "An approver identity is required");
        }
        executors.get(runId).submit(new EngineEvent.ApprovalSubmitted(approvalId, decision));
    }

    public void answer(String runId, String stageId, Map<String, String> answers, String actor) {
        RunState run = get(runId);
        synchronized (run) {
            if (!run.pendingQuestions().containsKey(stageId)) {
                throw new EngineException(EngineException.Reason.CONFLICT, "No questions pending for " + stageId);
            }
        }
        executors.get(runId).submit(new EngineEvent.AnswersSubmitted(stageId, Map.copyOf(answers), actor));
    }

    public void stop(String runId, String reason, String actor) {
        RunState run = get(runId);
        if (run.status().isTerminal()) {
            throw new EngineException(EngineException.Reason.CONFLICT, "Run already " + run.status());
        }
        executors.get(runId).submit(new EngineEvent.StopRequested(reason, actor));
    }

    /** Human replaces an accepted artifact; downstream stages are invalidated and re-planned. */
    public void override(String runId, String stageId, JsonNode content, String actor, String reason) {
        RunState run = get(runId);
        Object typed;
        synchronized (run) {
            if (run.status().isTerminal()) {
                throw new EngineException(EngineException.Reason.CONFLICT, "Run already " + run.status());
            }
            ArtifactVersion current = run.artifacts().latestAccepted(stageId).orElseThrow(() ->
                    new EngineException(EngineException.Reason.CONFLICT, "Stage " + stageId + " has no accepted artifact"));
            try {
                typed = services.json().treeToValue(content, current.content().getClass());
            } catch (IOException | IllegalArgumentException e) {
                throw new EngineException(EngineException.Reason.INVALID,
                        "Content does not match " + current.content().getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        executors.get(runId).submit(new EngineEvent.ArtifactOverridden(stageId, typed, actor, reason));
    }

    public RunStatus awaitCompletion(String runId, Duration timeout) throws InterruptedException, TimeoutException {
        try {
            return get(runId).completion().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    public EngineServices services() {
        return services;
    }
}
