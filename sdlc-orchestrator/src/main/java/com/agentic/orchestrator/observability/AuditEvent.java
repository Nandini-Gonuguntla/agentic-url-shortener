package com.agentic.orchestrator.observability;

import java.time.Instant;
import java.util.Map;

/**
 * One immutable audit entry. {@code hash} = SHA-256(prevHash + canonical form of every other field),
 * so editing or deleting any earlier entry breaks the chain from that point on.
 */
public record AuditEvent(
        long seq,
        Instant timestamp,
        String runId,
        String traceId,
        String spanId,
        String stageId,
        String actor,
        String type,
        Map<String, Object> data,
        String prevHash,
        String hash) {
}
