package com.agentic.orchestrator.engine;

import java.time.Instant;
import java.util.List;

/**
 * Decision lineage: who decided what, why, and on which artifact versions.
 * Types: APPROVAL, REJECTION, CLARIFICATION, RETRY, REWORK, FALLBACK, REPLAN, OVERRIDE, ROLLBACK, STOP.
 */
public record DecisionRecord(int id, Instant at, String stageId, String type, String actor, String summary,
                             String rationale, List<String> basedOn) {
}
