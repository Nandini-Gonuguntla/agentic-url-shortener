package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.workflow.StageDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, serializable view of a run for the API, dashboard and CLI. */
public record RunSnapshot(
        String runId,
        String scenarioId,
        String title,
        String requirement,
        String approvalPolicy,
        RunStatus status,
        String statusReason,
        Instant createdAt,
        Instant endedAt,
        long durationMs,
        String traceId,
        List<StageView> stages,
        List<ApprovalRequest> pendingApprovals,
        List<Question> pendingQuestions,
        List<DecisionRecord> decisions,
        List<ArtifactRef> artifacts,
        Map<String, Long> counters,
        int agentInvocations,
        int reworkCycles,
        String workspace,
        String outputDirectory,
        String mermaid) {

    public record StageView(
            String id,
            String agent,
            String description,
            StageStatus status,
            List<String> dependsOn,
            String approval,
            String reworkTarget,
            String fallbackAgent,
            int generation,
            int executions,
            boolean usingFallback,
            Instant startedAt,
            Instant endedAt,
            String lastError,
            List<StageAttempt> history) {
    }

    public record ArtifactRef(String ref, String stageId, int version, String state, String producedBy,
                              String contentHash, Instant createdAt) {
    }

    static RunSnapshot of(RunState run) {
        Map<String, String> statuses = new LinkedHashMap<>();
        List<StageView> stages = run.graph().topologicalOrder().stream().map(id -> {
            StageDefinition def = run.graph().stage(id);
            StageState st = run.stage(id);
            statuses.put(id, st.status().name());
            return new StageView(id, st.usingFallback() ? def.fallbackAgent() : def.agent(), def.description(),
                    st.status(), def.dependsOn().stream().sorted().toList(), def.approval().name(), def.reworkTarget(),
                    def.fallbackAgent(), st.generation(), st.executions(), st.usingFallback(), st.startedAt(),
                    st.endedAt(), st.lastError(), st.history());
        }).toList();
        List<ArtifactRef> artifacts = run.artifacts().all().stream()
                .map(v -> new ArtifactRef(v.ref(), v.stageId(), v.version(), v.state().name(), v.producedBy(),
                        v.contentHash(), v.createdAt()))
                .toList();
        Instant end = run.endedAt() != null ? run.endedAt() : Instant.now();
        return new RunSnapshot(run.runId(), run.scenario().id(), run.scenario().title(), run.scenario().requirement(),
                run.options().approvals().name(), run.status(), run.statusReason(), run.createdAt(), run.endedAt(),
                Duration.between(run.createdAt(), end).toMillis(), run.audit().traceId(), stages,
                List.copyOf(run.pendingApprovals().values()),
                run.pendingQuestions().values().stream().flatMap(List::stream).toList(),
                run.decisions(), artifacts, run.counters(), run.agentInvocations(), run.reworkCycles(),
                run.workspace().root().toString(), run.outputDir().toString(), run.graph().toMermaid(statuses));
    }
}
