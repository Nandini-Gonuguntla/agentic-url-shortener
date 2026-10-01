package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.RiskLevel;

import java.util.List;

public record RiskAssessment(int score, RiskLevel level, List<String> factors) {
}
