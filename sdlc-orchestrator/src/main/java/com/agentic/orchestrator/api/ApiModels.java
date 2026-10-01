package com.agentic.orchestrator.api;

import com.agentic.orchestrator.engine.ApprovalVerdict;
import com.agentic.orchestrator.engine.RunStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public final class ApiModels {

    private ApiModels() {
    }

    /** Either a catalog scenario or a free-text requirement (live mode). approvals: MANUAL (default) or AUTO. */
    public record StartRunRequest(String scenarioId, String requirement, String title, String approvals) {
    }

    public record DecisionRequest(@NotNull ApprovalVerdict verdict, @NotBlank String approver, String comment,
                                  String reworkStage) {
    }

    public record AnswersRequest(@NotNull Map<String, String> answers, @NotBlank String actor) {
    }

    public record StopRequest(String reason, @NotBlank String actor) {
    }

    public record OverrideRequest(@NotNull JsonNode content, @NotBlank String actor, @NotBlank String reason) {
    }

    public record RunSummary(String runId, String scenarioId, String title, RunStatus status, String statusReason,
                             Instant createdAt, long durationMs, int pendingApprovals, int pendingQuestions) {
    }
}
