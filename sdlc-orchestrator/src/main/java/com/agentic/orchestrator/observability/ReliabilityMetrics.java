package com.agentic.orchestrator.observability;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cross-run reliability metrics: success rate, retry/rework/rollback frequency, MTTR,
 * end-to-end latency, approval wait time. Persisted so they survive restarts.
 */
public final class ReliabilityMetrics {

    private final Path file;
    private final ObjectMapper json;
    private final Map<String, Long> counters = new TreeMap<>();
    private final List<Long> runLatenciesMs = new ArrayList<>();
    private final List<Long> recoveryTimesMs = new ArrayList<>();
    private final List<Long> approvalWaitsMs = new ArrayList<>();
    private final Map<String, List<Long>> stageLatenciesMs = new TreeMap<>();

    public ReliabilityMetrics(Path file, ObjectMapper json) {
        this.file = file;
        this.json = json;
        load();
    }

    public synchronized void increment(String counter) {
        counters.merge(counter, 1L, Long::sum);
    }

    public synchronized void add(String counter, long amount) {
        counters.merge(counter, amount, Long::sum);
    }

    public synchronized void stageCompleted(String stageId, Duration latency) {
        increment("stage.executions");
        stageLatenciesMs.computeIfAbsent(stageId, k -> new ArrayList<>()).add(latency.toMillis());
    }

    /** Time from a stage's first failure to its eventual success: the "repair" in MTTR. */
    public synchronized void recovered(Duration timeToRecover) {
        increment("stage.recoveries");
        recoveryTimesMs.add(timeToRecover.toMillis());
    }

    public synchronized void approvalDecided(String verdict, Duration wait) {
        increment("approvals." + verdict.toLowerCase());
        approvalWaitsMs.add(wait.toMillis());
    }

    public synchronized void runFinished(String status, Duration latency) {
        increment("runs." + status.toLowerCase());
        runLatenciesMs.add(latency.toMillis());
        save();
    }

    public synchronized Snapshot snapshot() {
        long succeeded = counters.getOrDefault("runs.succeeded", 0L);
        long finished = succeeded + counters.getOrDefault("runs.failed", 0L) + counters.getOrDefault("runs.stopped", 0L);
        long stageExecutions = counters.getOrDefault("stage.executions", 0L);
        Map<String, Double> perStage = new LinkedHashMap<>();
        stageLatenciesMs.forEach((stage, values) -> perStage.put(stage, average(values)));
        return new Snapshot(
                Map.copyOf(counters),
                ratio(succeeded, finished),
                ratio(counters.getOrDefault("stage.retries", 0L), stageExecutions),
                ratio(counters.getOrDefault("stage.reworks", 0L), finished),
                ratio(counters.getOrDefault("workspace.rollbacks", 0L), finished),
                average(recoveryTimesMs),
                new Latency(average(runLatenciesMs), percentile(runLatenciesMs, 50), percentile(runLatenciesMs, 95),
                        runLatenciesMs.isEmpty() ? 0 : Collections.max(runLatenciesMs)),
                average(approvalWaitsMs),
                perStage);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round(1000.0 * numerator / denominator) / 1000.0;
    }

    private static double average(List<Long> values) {
        return values.isEmpty() ? 0.0 : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            Persisted persisted = json.readValue(file.toFile(), Persisted.class);
            counters.putAll(persisted.counters());
            runLatenciesMs.addAll(persisted.runLatenciesMs());
            recoveryTimesMs.addAll(persisted.recoveryTimesMs());
            approvalWaitsMs.addAll(persisted.approvalWaitsMs());
            persisted.stageLatenciesMs().forEach((k, v) -> stageLatenciesMs.put(k, new ArrayList<>(v)));
        } catch (IOException e) {
            // Corrupt metrics must not stop the orchestrator; start fresh and keep the bad file for inspection.
            counters.put("metrics.load_failures", 1L);
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),
                    new Persisted(counters, runLatenciesMs, recoveryTimesMs, approvalWaitsMs, stageLatenciesMs));
        } catch (IOException e) {
            counters.merge("metrics.save_failures", 1L, Long::sum);
        }
    }

    public record Latency(double avgMs, long p50Ms, long p95Ms, long maxMs) {
    }

    public record Snapshot(
            Map<String, Long> counters,
            double runSuccessRate,
            double retryRatePerStageExecution,
            double reworksPerRun,
            double rollbacksPerRun,
            double mttrMs,
            Latency endToEndLatency,
            double avgApprovalWaitMs,
            Map<String, Double> avgStageLatencyMs) {
    }

    record Persisted(Map<String, Long> counters, List<Long> runLatenciesMs, List<Long> recoveryTimesMs,
                     List<Long> approvalWaitsMs, Map<String, List<Long>> stageLatenciesMs) {
    }
}
