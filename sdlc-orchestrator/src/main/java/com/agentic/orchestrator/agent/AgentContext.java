package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.engine.ArtifactStore;
import com.agentic.orchestrator.engine.RiskAssessment;
import com.agentic.orchestrator.engine.RunSnapshot;
import com.agentic.orchestrator.observability.AuditLog;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workspace.Workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Everything an agent may see for one execution: accepted upstream artifacts (cross-stage
 * context), feedback from failed gates or reviewers, stakeholder clarifications, and a read view
 * of the workspace.
 */
public record AgentContext(
        String runId,
        ScenarioDefinition scenario,
        StageDefinition stage,
        int execution,
        int attempt,
        List<String> feedback,
        Map<String, String> clarifications,
        ArtifactStore artifacts,
        Workspace workspace,
        LlmClient llm,
        AgentTelemetry telemetry,
        Supplier<RunSnapshot> runView,
        Supplier<RiskAssessment> risk,
        Supplier<AuditLog.Verification> auditCheck,
        Path outputDir) {

    public <T> T require(String stageId, Class<T> type) {
        return artifacts.accepted(stageId, type).orElseThrow(() ->
                new IllegalStateException("Missing accepted " + type.getSimpleName() + " from stage " + stageId));
    }

    public <T> Optional<T> optional(String stageId, Class<T> type) {
        return artifacts.accepted(stageId, type);
    }
}
