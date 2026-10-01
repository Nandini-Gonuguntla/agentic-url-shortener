package com.agentic.orchestrator.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only, hash-chained audit trail for one run, written as JSON Lines. Every state change,
 * agent call, gate decision, approval and rollback lands here with trace/span ids.
 */
public final class AuditLog {

    public static final String GENESIS = "0".repeat(64);

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    private final String runId;
    private final String traceId;
    private final Path file;
    private final Clock clock;
    private final List<AuditEvent> events = new ArrayList<>();
    private String lastHash = GENESIS;
    private long seq;

    public AuditLog(String runId, String traceId, Path file, Clock clock) {
        this.runId = runId;
        this.traceId = traceId;
        this.file = file;
        this.clock = clock;
    }

    public synchronized AuditEvent record(String type, String stageId, String spanId, String actor, Map<String, Object> data) {
        Map<String, Object> payload = data == null ? Map.of() : new LinkedHashMap<>(data);
        long next = ++seq;
        AuditEvent unsigned = new AuditEvent(next, clock.instant(), runId, traceId, spanId, stageId, actor, type,
                payload, lastHash, null);
        String hash = hash(unsigned);
        AuditEvent event = new AuditEvent(next, unsigned.timestamp(), runId, traceId, spanId, stageId, actor, type,
                payload, lastHash, hash);
        events.add(event);
        lastHash = hash;
        append(event);
        return event;
    }

    public synchronized List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public String traceId() {
        return traceId;
    }

    /** Re-reads the file from disk and recomputes the chain, so tampering with the file is detected. */
    public synchronized Verification verify() {
        List<String> lines;
        try {
            lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            return new Verification(false, 0, 0, "Cannot read audit file: " + e.getMessage());
        }
        String expectedPrev = GENESIS;
        long index = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            index++;
            AuditEvent event;
            try {
                event = CANONICAL.readValue(line, AuditEvent.class);
            } catch (JsonProcessingException e) {
                return new Verification(false, lines.size(), index, "Unparseable entry at line " + index);
            }
            if (!expectedPrev.equals(event.prevHash())) {
                return new Verification(false, lines.size(), index, "Chain broken at seq " + event.seq());
            }
            if (!hash(event).equals(event.hash())) {
                return new Verification(false, lines.size(), index, "Entry modified at seq " + event.seq());
            }
            expectedPrev = event.hash();
        }
        return new Verification(true, index, index, "Chain intact");
    }

    static String hash(AuditEvent event) {
        try {
            String canonical = String.join("|",
                    String.valueOf(event.seq()), event.timestamp().toString(), event.runId(),
                    String.valueOf(event.traceId()), String.valueOf(event.spanId()), String.valueOf(event.stageId()),
                    String.valueOf(event.actor()), event.type(), CANONICAL.writeValueAsString(event.data()));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(event.prevHash().getBytes(StandardCharsets.UTF_8));
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash audit event", e);
        }
    }

    private void append(AuditEvent event) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, CANONICAL.writeValueAsString(event) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Audit write failed; refusing to continue without an audit trail", e);
        }
    }

    public record Verification(boolean valid, long entries, long checked, String message) {
    }
}
