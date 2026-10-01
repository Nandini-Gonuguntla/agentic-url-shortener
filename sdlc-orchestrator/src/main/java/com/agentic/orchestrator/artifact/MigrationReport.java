package com.agentic.orchestrator.artifact;

import java.util.List;

public record MigrationReport(boolean safe, List<String> migrationsAdded, List<Finding> findings) {
}
