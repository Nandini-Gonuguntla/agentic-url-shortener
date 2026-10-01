package com.agentic.orchestrator.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @TempDir
    Path tmp;

    @Test
    void entriesAreHashChainedAndVerifiable() {
        AuditLog log = new AuditLog("run-1", Ids.traceId(), tmp.resolve("audit.jsonl"), Clock.systemUTC());
        AuditEvent first = log.record("RUN_STARTED", null, null, "system", Map.of("a", 1));
        AuditEvent second = log.record("STAGE_STARTED", "design", Ids.spanId(), "agent:design", Map.of());

        assertThat(first.prevHash()).isEqualTo(AuditLog.GENESIS);
        assertThat(second.prevHash()).isEqualTo(first.hash());
        assertThat(log.verify().valid()).isTrue();
        assertThat(log.verify().entries()).isEqualTo(2);
    }

    @Test
    void editingAnEntryOnDiskIsDetected() throws Exception {
        Path file = tmp.resolve("audit.jsonl");
        AuditLog log = new AuditLog("run-1", Ids.traceId(), file, Clock.systemUTC());
        log.record("APPROVAL_DECIDED", "release", null, "human:alice", Map.of("verdict", "REJECT"));
        log.record("RUN_SUCCEEDED", null, null, "system", Map.of());

        List<String> lines = Files.readAllLines(file);
        Files.write(file, List.of(lines.get(0).replace("REJECT", "APPROVE"), lines.get(1)));

        AuditLog.Verification result = log.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("seq 1");
    }

    @Test
    void deletingAnEntryBreaksTheChain() throws Exception {
        Path file = tmp.resolve("audit.jsonl");
        AuditLog log = new AuditLog("run-1", Ids.traceId(), file, Clock.systemUTC());
        log.record("A", null, null, "system", Map.of());
        log.record("B", null, null, "system", Map.of());
        log.record("C", null, null, "system", Map.of());

        List<String> lines = Files.readAllLines(file);
        Files.write(file, List.of(lines.get(0), lines.get(2)));

        assertThat(log.verify().valid()).isFalse();
    }
}
