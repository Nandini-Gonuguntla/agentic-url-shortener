package com.agentic.orchestrator.artifact;

import java.util.List;

public record SecurityReport(String summary, int filesReviewed, List<Finding> findings) {
}
