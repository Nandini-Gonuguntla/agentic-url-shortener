package com.agentic.orchestrator.artifact;

import java.util.List;

public record DocsUpdate(String summary, List<FileChange> changes) {
}
