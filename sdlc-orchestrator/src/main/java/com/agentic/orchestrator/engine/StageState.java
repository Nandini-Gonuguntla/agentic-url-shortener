package com.agentic.orchestrator.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable execution state of one stage. Only the run's scheduler thread mutates it.
 * {@code generation} increments whenever the stage is invalidated (rework or upstream change),
 * which lets the engine discard results from agents that were working on stale inputs.
 */
public final class StageState {

    private final String id;
    private StageStatus status = StageStatus.PENDING;
    private int generation;
    private int executions;
    private int attemptsInGeneration;
    private boolean usingFallback;
    private Instant startedAt;
    private Instant endedAt;
    private Instant deadline;
    private Instant notBefore;
    private Instant firstFailureAt;
    private String spanId;
    private String currentAgent;
    private String lastError;
    private String checkpointBefore;
    private StageOutcome pendingOutcome;
    private final List<StageAttempt> history = new ArrayList<>();

    public StageState(String id) {
        this.id = id;
    }

    void begin(Instant now, String spanId, String agent, Duration timeout) {
        executions++;
        attemptsInGeneration++;
        status = StageStatus.RUNNING;
        startedAt = now;
        endedAt = null;
        deadline = now.plus(timeout);
        notBefore = null;
        this.spanId = spanId;
        this.currentAgent = agent;
    }

    void end(Instant now, String result, String detail) {
        endedAt = now;
        history.add(new StageAttempt(executions, generation, currentAgent, result, startedAt, now, detail));
    }

    void invalidate() {
        generation++;
        status = StageStatus.PENDING;
        attemptsInGeneration = 0;
        usingFallback = false;
        pendingOutcome = null;
        notBefore = null;
        deadline = null;
        checkpointBefore = null;
    }

    public String id() { return id; }
    public StageStatus status() { return status; }
    public int generation() { return generation; }
    public int executions() { return executions; }
    public int attemptsInGeneration() { return attemptsInGeneration; }
    public boolean usingFallback() { return usingFallback; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public Instant deadline() { return deadline; }
    public Instant notBefore() { return notBefore; }
    public Instant firstFailureAt() { return firstFailureAt; }
    public String spanId() { return spanId; }
    public String currentAgent() { return currentAgent; }
    public String lastError() { return lastError; }
    public String checkpointBefore() { return checkpointBefore; }
    public StageOutcome pendingOutcome() { return pendingOutcome; }
    public List<StageAttempt> history() { return List.copyOf(history); }

    void status(StageStatus value) { this.status = value; }
    void notBefore(Instant value) { this.notBefore = value; }
    void firstFailureAt(Instant value) { this.firstFailureAt = value; }
    void lastError(String value) { this.lastError = value; }
    void checkpointBefore(String value) { this.checkpointBefore = value; }
    void pendingOutcome(StageOutcome value) { this.pendingOutcome = value; }
    void resetAttempts() { this.attemptsInGeneration = 0; }

    void activateFallback() {
        usingFallback = true;
        attemptsInGeneration = 0;
    }
}
