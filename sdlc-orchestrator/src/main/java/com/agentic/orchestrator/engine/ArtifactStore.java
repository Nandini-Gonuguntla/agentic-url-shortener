package com.agentic.orchestrator.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Versioned cross-stage context. Agents read accepted upstream artifacts; the scheduler writes.
 * Content hashes let decision records say exactly which input version a decision was based on.
 */
public final class ArtifactStore {

    private final ObjectMapper json;
    private final Map<String, List<ArtifactVersion>> versions = new ConcurrentHashMap<>();

    public ArtifactStore(ObjectMapper json) {
        this.json = json;
    }

    ArtifactVersion put(String stageId, int generation, Object content, String producedBy, Instant now) {
        List<ArtifactVersion> list = versions.computeIfAbsent(stageId, k -> new CopyOnWriteArrayList<>());
        list.forEach(v -> {
            if (v.state() == ArtifactVersion.State.ACCEPTED || v.state() == ArtifactVersion.State.CANDIDATE) {
                v.state(ArtifactVersion.State.SUPERSEDED);
            }
        });
        ArtifactVersion version = new ArtifactVersion(stageId, list.size() + 1, generation, now, producedBy,
                hash(content), content);
        list.add(version);
        return version;
    }

    void supersede(String stageId) {
        versions.getOrDefault(stageId, List.of()).forEach(v -> {
            if (v.state() != ArtifactVersion.State.REJECTED) {
                v.state(ArtifactVersion.State.SUPERSEDED);
            }
        });
    }

    public Optional<ArtifactVersion> latestAccepted(String stageId) {
        List<ArtifactVersion> list = versions.getOrDefault(stageId, List.of());
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).state() == ArtifactVersion.State.ACCEPTED) {
                return Optional.of(list.get(i));
            }
        }
        return Optional.empty();
    }

    public Optional<ArtifactVersion> latest(String stageId) {
        List<ArtifactVersion> list = versions.getOrDefault(stageId, List.of());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
    }

    public <T> Optional<T> accepted(String stageId, Class<T> type) {
        return latestAccepted(stageId).map(ArtifactVersion::content).filter(type::isInstance).map(type::cast);
    }

    public Optional<ArtifactVersion> version(String stageId, int version) {
        return versions.getOrDefault(stageId, List.of()).stream().filter(v -> v.version() == version).findFirst();
    }

    public List<ArtifactVersion> all() {
        List<ArtifactVersion> result = new ArrayList<>();
        versions.values().forEach(result::addAll);
        result.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        return result;
    }

    private String hash(Object content) {
        try {
            byte[] bytes = json.writeValueAsString(content).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            return "unhashable";
        }
    }
}
