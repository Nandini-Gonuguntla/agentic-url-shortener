package com.agentic.orchestrator.artifact;

public record Finding(Severity severity, String rule, String path, int line, String message) {
}
