package com.agentic.orchestrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic agent responses recorded per scenario ("cassettes"). Same agent code, same
 * prompts and same gates as live mode; only the model call is replaced. Makes demos and tests
 * reproducible and runnable without an API key.
 *
 * <p>Lookup: {@code scenarios/<id>/replay/<key>.attempt-<execution>.json}, then {@code <key>.json}.
 * String values of the form {@code @file:path} are replaced with that file's content, so source
 * code fixtures live as real files rather than escaped JSON strings.
 */
public class ReplayLlmClient implements LlmClient {

    private static final String FILE_REF = "@file:";

    private final Path scenariosDir;
    private final ObjectMapper json;

    public ReplayLlmClient(Path scenariosDir, ObjectMapper json) {
        this.scenariosDir = scenariosDir;
        this.json = json;
    }

    @Override
    public <T> LlmResponse<T> generate(LlmRequest request, Class<T> type) {
        long start = System.nanoTime();
        Path fixture = fixtureFor(request).orElseThrow(() -> new LlmException(
                "No recorded response for '" + request.replayKey() + "' in scenario '" + request.scenarioId()
                        + "'. Custom requirements need live mode (set ANTHROPIC_API_KEY)."));
        try {
            JsonNode tree = json.readTree(fixture.toFile());
            resolveFileRefs(tree, fixture.getParent());
            T value = json.treeToValue(tree, type);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new LlmResponse<>(value, "replay", "recorded", fixture.getFileName().toString(), 0, 0, latencyMs,
                    false, null);
        } catch (IOException e) {
            throw new LlmException("Invalid fixture " + fixture + ": " + e.getMessage(), e);
        }
    }

    public boolean hasFixture(LlmRequest request) {
        return fixtureFor(request).isPresent();
    }

    private Optional<Path> fixtureFor(LlmRequest request) {
        Path dir = scenariosDir.resolve(request.scenarioId()).resolve("replay");
        for (String name : List.of(request.replayKey() + ".attempt-" + request.execution() + ".json",
                request.replayKey() + ".json")) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private void resolveFileRefs(JsonNode node, Path baseDir) throws IOException {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isTextual() && value.asText().startsWith(FILE_REF)) {
                    field.setValue(new TextNode(readRef(baseDir, value.asText())));
                } else {
                    resolveFileRefs(value, baseDir);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode value = array.get(i);
                if (value.isTextual() && value.asText().startsWith(FILE_REF)) {
                    array.set(i, new TextNode(readRef(baseDir, value.asText())));
                } else {
                    resolveFileRefs(value, baseDir);
                }
            }
        }
    }

    private static String readRef(Path baseDir, String ref) throws IOException {
        Path file = baseDir.resolve(ref.substring(FILE_REF.length())).normalize();
        if (!file.startsWith(baseDir)) {
            throw new IOException("Fixture reference escapes fixture directory: " + ref);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Override
    public String mode() {
        return "replay";
    }
}
