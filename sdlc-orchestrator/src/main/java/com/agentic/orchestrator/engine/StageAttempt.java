package com.agentic.orchestrator.engine;

import java.time.Instant;

public record StageAttempt(int execution, int generation, String agent, String result, Instant startedAt,
                           Instant endedAt, String detail) {
}
