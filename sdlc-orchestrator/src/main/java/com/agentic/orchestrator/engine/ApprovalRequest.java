package com.agentic.orchestrator.engine;

import java.time.Instant;
import java.util.List;

public record ApprovalRequest(String id, String stageId, int generation, List<String> reasons, String riskLevel,
                              int riskScore, String artifactRef, Instant requestedAt) {
}
