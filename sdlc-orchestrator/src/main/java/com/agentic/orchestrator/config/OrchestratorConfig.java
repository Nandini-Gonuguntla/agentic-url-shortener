package com.agentic.orchestrator.config;

import com.agentic.orchestrator.agent.AgentRegistry;
import com.agentic.orchestrator.agent.CodebaseAnalysisAgent;
import com.agentic.orchestrator.agent.DesignAgent;
import com.agentic.orchestrator.agent.DocumentationAgent;
import com.agentic.orchestrator.agent.ImplementationAgent;
import com.agentic.orchestrator.agent.MigrationSafetyAgent;
import com.agentic.orchestrator.agent.PlanningAgent;
import com.agentic.orchestrator.agent.PublishAgent;
import com.agentic.orchestrator.agent.ReleaseReadinessAgent;
import com.agentic.orchestrator.agent.RequirementsAgent;
import com.agentic.orchestrator.agent.SecurityReviewAgent;
import com.agentic.orchestrator.agent.StaticCodebaseAnalysisAgent;
import com.agentic.orchestrator.agent.TemplateDocumentationAgent;
import com.agentic.orchestrator.agent.ThreatModelAgent;
import com.agentic.orchestrator.agent.ValidationAgent;
import com.agentic.orchestrator.engine.AutonomyLimits;
import com.agentic.orchestrator.engine.EngineServices;
import com.agentic.orchestrator.engine.Replanner;
import com.agentic.orchestrator.engine.RiskAssessor;
import com.agentic.orchestrator.engine.WorkflowEngine;
import com.agentic.orchestrator.gate.GateRegistry;
import com.agentic.orchestrator.gate.StandardGates;
import com.agentic.orchestrator.llm.AnthropicLlmClient;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.ReplayLlmClient;
import com.agentic.orchestrator.llm.RoutingLlmClient;
import com.agentic.orchestrator.observability.ReliabilityMetrics;
import com.agentic.orchestrator.policy.PolicyEngine;
import com.agentic.orchestrator.scenario.ScenarioCatalog;
import com.agentic.orchestrator.workspace.CodebaseScanner;
import com.agentic.orchestrator.workspace.GitWorkspaceFactory;
import com.agentic.orchestrator.workspace.MavenRunner;
import com.agentic.orchestrator.workspace.ProcessRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OrchestratorConfig {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorConfig.class);

    @Bean
    ProjectLayout projectLayout(OrchestratorProperties props) {
        ProjectLayout layout = ProjectLayout.resolve(props);
        log.info("Repository root {}, target project {}, runs in {}", layout.repoRoot(), layout.targetProject(), layout.runsDir());
        return layout;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProcessRunner processRunner() {
        return new ProcessRunner();
    }

    @Bean
    ScenarioCatalog scenarioCatalog(ProjectLayout layout, ObjectMapper json) {
        return new ScenarioCatalog(layout.scenariosDir(), json);
    }

    @Bean
    ReliabilityMetrics reliabilityMetrics(ProjectLayout layout, ObjectMapper json) {
        return new ReliabilityMetrics(layout.runsDir().resolve("metrics.json"), json);
    }

    @Bean
    LlmClient llmClient(OrchestratorProperties props, ProjectLayout layout, ObjectMapper json) {
        ReplayLlmClient replay = new ReplayLlmClient(layout.scenariosDir(), json);
        String mode = props.llm().mode().toLowerCase();
        boolean hasKey = System.getenv("ANTHROPIC_API_KEY") != null && !System.getenv("ANTHROPIC_API_KEY").isBlank();
        if (mode.equals("replay") || (mode.equals("auto") && !hasKey)) {
            log.info("LLM mode: replay (recorded agent responses){}", hasKey ? "" : "; set ANTHROPIC_API_KEY for live mode");
            return replay;
        }
        log.info("LLM mode: live ({}) with replay fallback", props.llm().model());
        return new RoutingLlmClient(new AnthropicLlmClient(props.llm().model(), props.llm().serverSideFallback()), replay);
    }

    @Bean
    PolicyEngine policyEngine() {
        return PolicyEngine.standard();
    }

    @Bean
    GateRegistry gateRegistry() {
        return StandardGates.registry();
    }

    @Bean
    AgentRegistry agentRegistry(ObjectMapper json, PolicyEngine policy, ProcessRunner processes, OrchestratorProperties props) {
        CodebaseScanner scanner = new CodebaseScanner();
        return new AgentRegistry()
                .register(new RequirementsAgent(json))
                .register(new CodebaseAnalysisAgent(json, scanner))
                .register(new StaticCodebaseAnalysisAgent(scanner))
                .register(new DesignAgent(json))
                .register(new ThreatModelAgent(json))
                .register(new PlanningAgent(json))
                .register(new ImplementationAgent(json))
                .register(new ValidationAgent(new MavenRunner(processes, props.mavenOffline())))
                .register(new SecurityReviewAgent(policy))
                .register(new MigrationSafetyAgent())
                .register(new DocumentationAgent(json))
                .register(new TemplateDocumentationAgent())
                .register(new ReleaseReadinessAgent())
                .register(new PublishAgent(json));
    }

    @Bean
    AutonomyLimits autonomyLimits(OrchestratorProperties props) {
        OrchestratorProperties.Autonomy a = props.autonomy();
        return new AutonomyLimits(a.maxAgentInvocations(), a.maxRunDuration(), a.maxReworkCycles(), a.approvalTimeout(),
                a.approvalRiskThreshold(), a.maxFilesPerChange());
    }

    @Bean
    EngineServices engineServices(AgentRegistry agents, GateRegistry gates, PolicyEngine policy, ReliabilityMetrics metrics,
                                  LlmClient llm, AutonomyLimits limits, ObjectMapper json, Clock clock) {
        return new EngineServices(agents, gates, policy, new RiskAssessor(), new Replanner(), metrics, llm, limits, json, clock);
    }

    @Bean
    WorkflowEngine workflowEngine(EngineServices services, ProjectLayout layout, ProcessRunner processes) {
        return new WorkflowEngine(services, new GitWorkspaceFactory(layout.targetProject(), layout.repoRoot(), processes),
                layout.runsDir());
    }
}
