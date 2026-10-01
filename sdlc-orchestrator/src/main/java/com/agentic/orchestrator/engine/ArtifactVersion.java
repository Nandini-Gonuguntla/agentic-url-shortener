package com.agentic.orchestrator.engine;

import java.time.Instant;

/** One version of a stage's output, with provenance. Older versions are kept for lineage. */
public final class ArtifactVersion {

    public enum State { CANDIDATE, ACCEPTED, REJECTED, SUPERSEDED }

    private final String stageId;
    private final int version;
    private final int generation;
    private final Instant createdAt;
    private final String producedBy;
    private final String contentHash;
    private final Object content;
    private volatile State state = State.CANDIDATE;

    ArtifactVersion(String stageId, int version, int generation, Instant createdAt, String producedBy,
                    String contentHash, Object content) {
        this.stageId = stageId;
        this.version = version;
        this.generation = generation;
        this.createdAt = createdAt;
        this.producedBy = producedBy;
        this.contentHash = contentHash;
        this.content = content;
    }

    public String ref() {
        return stageId + "@v" + version;
    }

    public String stageId() { return stageId; }
    public int version() { return version; }
    public int generation() { return generation; }
    public Instant createdAt() { return createdAt; }
    public String producedBy() { return producedBy; }
    public String contentHash() { return contentHash; }
    public Object content() { return content; }
    public State state() { return state; }

    void state(State value) { this.state = value; }
}
