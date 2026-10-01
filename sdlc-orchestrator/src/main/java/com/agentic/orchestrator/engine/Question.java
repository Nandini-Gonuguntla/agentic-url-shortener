package com.agentic.orchestrator.engine;

import java.time.Instant;
import java.util.List;

public record Question(String id, String stageId, String question, List<String> options, String recommendedOption,
                       String impact, Instant askedAt) {
}
