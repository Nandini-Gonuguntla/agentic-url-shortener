package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agent.Agent;
import com.agentic.orchestrator.agent.AgentContext;
import com.agentic.orchestrator.agent.AgentRegistry;
import com.agentic.orchestrator.artifact.RiskLevel;
import com.agentic.orchestrator.gate.GateRegistry;
import com.agentic.orchestrator.gate.StandardGates;
import com.agentic.orchestrator.llm.ReplayLlmClient;
import com.agentic.orchestrator.observability.ReliabilityMetrics;
import com.agentic.orchestrator.policy.PolicyEngine;
import com.agentic.orchestrator.scenario.ScenarioDefinition;
import com.agentic.orchestrator.scenario.ScenarioType;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import com.agentic.orchestrator.workspace.GitWorkspaceFactory;
import com.agentic.orchestrator.workspace.ProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Real engine, real git workspace in a temp dir, fake agents: fast tests of orchestration behaviour. */
final class EngineHarness {

    static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    final AgentRegistry agents = new AgentRegistry();
    final GateRegistry gates = StandardGates.registry();
    final ReliabilityMetrics metrics = new ReliabilityMetrics(null, JSON);
    private final Path root;
    private Replanner replanner = new Replanner();
    private AutonomyLimits limits = new AutonomyLimits(50, Duration.ofMinutes(5), 2, Duration.ofMinutes(5),
            RiskLevel.MEDIUM, 25);

    EngineHarness(Path root) throws IOException {
        this.root = root;
        Path project = root.resolve("project");
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("README.md"), "baseline\n");
        Files.writeString(project.resolve("src/App.txt"), "app v0\n");
    }

    EngineHarness agent(String name, Function<AgentContext, StageOutcome> body) {
        agents.register(new Agent() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StageOutcome execute(AgentContext context) {
                return body.apply(context);
            }
        });
        return this;
    }

    EngineHarness limits(AutonomyLimits value) {
        this.limits = value;
        return this;
    }

    EngineHarness replanner(Replanner value) {
        this.replanner = value;
        return this;
    }

    RunState start(RunOptions options, StageDefinition... stages) {
        EngineServices services = new EngineServices(agents, gates, PolicyEngine.standard(), new RiskAssessor(), replanner,
                metrics, new ReplayLlmClient(root.resolve("none"), JSON), limits, JSON, Clock.systemUTC());
        WorkflowEngine engine = new WorkflowEngine(services,
                new GitWorkspaceFactory(root.resolve("project"), root, new ProcessRunner()), root.resolve("runs"),
                () -> new WorkflowGraph(List.of(stages)));
        this.engine = engine;
        return engine.start(new ScenarioDefinition("test", "Test", ScenarioType.CUSTOM, "", "test requirement", Map.of()),
                options);
    }

    WorkflowEngine engine;

    static RunStatus await(RunState run) throws Exception {
        return run.completion().get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    static void waitUntil(BooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plusSeconds(30);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new TimeoutException("Condition not met in time");
            }
            Thread.sleep(50);
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
