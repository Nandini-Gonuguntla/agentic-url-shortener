package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.policy.PolicyReport;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import com.agentic.orchestrator.workspace.Workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All state of one run. Mutated only by its {@link RunExecutor} while holding the instance lock;
 * readers (REST API, CLI) take snapshots under the same lock.
 */
public final class RunState {

    private final String runId;
    private final ScenarioDefinition scenario;
    private final RunOptions options;
    private final WorkflowGraph graph;
    private final Map<String, StageState> stages = new LinkedHashMap<>();
    private final ArtifactStore artifacts;
    private final List<DecisionRecord> decisions = new ArrayList<>();
    private final Map<String, List<String>> feedback = new LinkedHashMap<>();
    private final Map<String, String> clarifications = new ConcurrentHashMap<>();
    private final Map<String, ApprovalRequest> pendingApprovals = new LinkedHashMap<>();
    private final Map<String, List<Question>> pendingQuestions = new LinkedHashMap<>();
    private final Map<String, PolicyReport> policyReports = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Workspace workspace;
    private final AuditLog audit;
    private final Path runDir;
    private final Instant createdAt;
    private final CompletableFuture<RunStatus> completion = new CompletableFuture<>();
    private RunStatus status = RunStatus.RUNNING;
    private String statusReason = "";
    private Instant endedAt;
    private int agentInvocations;
    private int reworkCycles;
    private int approvalSeq;

    public RunState(String runId, ScenarioDefinition scenario, RunOptions options, WorkflowGraph graph,
                    ArtifactStore artifacts, Workspace workspace, AuditLog audit, Path runDir, Instant createdAt) {
        this.runId = runId;
        this.scenario = scenario;
        this.options = options;
        this.graph = graph;
        this.artifacts = artifacts;
        this.workspace = workspace;
        this.audit = audit;
        this.runDir = runDir;
        this.createdAt = createdAt;
        graph.topologicalOrder().forEach(id -> stages.put(id, new StageState(id)));
    }

    public String runId() { return runId; }
    public ScenarioDefinition scenario() { return scenario; }
    public RunOptions options() { return options; }
    public WorkflowGraph graph() { return graph; }
    public ArtifactStore artifacts() { return artifacts; }
    public Workspace workspace() { return workspace; }
    public AuditLog audit() { return audit; }
    public Path runDir() { return runDir; }
    public Path outputDir() { return runDir.resolve("output"); }
    public Instant createdAt() { return createdAt; }
    public CompletableFuture<RunStatus> completion() { return completion; }
    public synchronized RunStatus status() { return status; }
    public synchronized String statusReason() { return statusReason; }
    public synchronized Instant endedAt() { return endedAt; }
    public Map<String, PolicyReport> policyReports() { return policyReports; }
    public Map<String, String> clarifications() { return clarifications; }

    public StageState stage(String id) {
        StageState state = stages.get(id);
        if (state == null) {
            throw new IllegalArgumentException("Unknown stage " + id);
        }
        return state;
    }

    public synchronized List<DecisionRecord> decisions() { return List.copyOf(decisions); }

    synchronized Map<String, StageState> stageStates() { return stages; }
    synchronized Map<String, ApprovalRequest> pendingApprovals() { return pendingApprovals; }
    synchronized Map<String, List<Question>> pendingQuestions() { return pendingQuestions; }

    synchronized List<String> feedbackFor(String stageId) {
        return List.copyOf(feedback.getOrDefault(stageId, List.of()));
    }

    synchronized void addFeedback(String stageId, String message) {
        feedback.computeIfAbsent(stageId, k -> new ArrayList<>()).add(message);
    }

    synchronized void clearFeedback(String stageId) {
        feedback.remove(stageId);
    }

    synchronized void addStage(StageDefinition definition) {
        stages.put(definition.id(), new StageState(definition.id()));
    }

    synchronized DecisionRecord decide(Instant at, String stageId, String type, String actor, String summary,
                                       String rationale, List<String> basedOn) {
        DecisionRecord record = new DecisionRecord(decisions.size() + 1, at, stageId, type, actor, summary,
                rationale, List.copyOf(basedOn));
        decisions.add(record);
        return record;
    }

    synchronized void status(RunStatus value, String reason, Instant at) {
        this.status = value;
        this.statusReason = reason == null ? "" : reason;
        if (value.isTerminal()) {
            this.endedAt = at;
        }
    }

    synchronized void count(String counter) {
        counters.merge(counter, 1L, Long::sum);
    }

    synchronized Map<String, Long> counters() { return Map.copyOf(counters); }

    synchronized int agentInvocations() { return agentInvocations; }
    synchronized void agentInvoked() { agentInvocations++; }
    synchronized int reworkCycles() { return reworkCycles; }
    synchronized void reworkStarted() { reworkCycles++; }
    synchronized String nextApprovalId() { return "apr-" + (++approvalSeq); }

    public synchronized RunSnapshot snapshot() {
        return RunSnapshot.of(this);
    }
}
