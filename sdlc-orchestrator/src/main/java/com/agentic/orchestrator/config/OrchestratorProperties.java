package com.agentic.orchestrator.config;

import com.agentic.orchestrator.artifact.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(
        String repoRoot,
        String targetProject,
        String scenariosDir,
        String runsDir,
        boolean mavenOffline,
        String apiToken,
        @Valid @NotNull Llm llm,
        @Valid @NotNull Autonomy autonomy) {

    /** @param mode auto (live when ANTHROPIC_API_KEY is set, else replay), live, or replay */
    public record Llm(@NotNull String mode, @NotNull String model, boolean serverSideFallback) {
    }

    public record Autonomy(
            @Min(1) int maxAgentInvocations,
            @NotNull Duration maxRunDuration,
            @Min(0) int maxReworkCycles,
            @NotNull Duration approvalTimeout,
            @NotNull RiskLevel approvalRiskThreshold,
            @Min(1) int maxFilesPerChange) {
    }
}
