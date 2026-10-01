package com.agentic.orchestrator.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Loads {@code scenarios/<id>/scenario.json}. */
public class ScenarioCatalog {

    private final Path dir;
    private final ObjectMapper json;

    public ScenarioCatalog(Path dir, ObjectMapper json) {
        this.dir = dir;
        this.json = json;
    }

    public List<ScenarioDefinition> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.map(child -> child.resolve("scenario.json"))
                    .filter(Files::isRegularFile)
                    .map(this::read)
                    .sorted(Comparator.comparing(ScenarioDefinition::id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<ScenarioDefinition> find(String id) {
        return list().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    private ScenarioDefinition read(Path file) {
        try {
            return json.readValue(file.toFile(), ScenarioDefinition.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid scenario file " + file, e);
        }
    }
}
