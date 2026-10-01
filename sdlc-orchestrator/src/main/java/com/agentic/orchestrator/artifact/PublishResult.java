package com.agentic.orchestrator.artifact;

import java.util.List;

public record PublishResult(String outputDirectory, List<String> files, int filesChanged, String patchFile) {
}
