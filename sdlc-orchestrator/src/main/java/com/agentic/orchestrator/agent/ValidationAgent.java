package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.ValidationReport;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.workspace.MavenRunner;

import java.util.Map;

/** Runs the real build and test suite; the gate, not the agent, decides whether the result is acceptable. */
public class ValidationAgent implements Agent {

    private final MavenRunner maven;

    public ValidationAgent(MavenRunner maven) {
        this.maven = maven;
    }

    @Override
    public String name() {
        return "validation";
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        ValidationReport report = maven.test(ctx.workspace().root(), ctx.stage().timeout().minusSeconds(30));
        ctx.telemetry().event(name(), "TOOL_CALL", Map.of("tool", "maven", "command", report.command(),
                "testsRun", report.testsRun(), "failures", report.failures(), "errors", report.errors(),
                "durationMs", report.durationMs()));
        return StageOutcome.success(report, "maven");
    }
}
