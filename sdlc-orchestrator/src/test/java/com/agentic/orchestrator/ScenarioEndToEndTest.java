package com.agentic.orchestrator;

import com.agentic.orchestrator.engine.RunOptions;
import com.agentic.orchestrator.engine.RunState;
import com.agentic.orchestrator.engine.RunStatus;
import com.agentic.orchestrator.engine.WorkflowEngine;
import com.agentic.orchestrator.scenario.ScenarioCatalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Full pipeline in replay mode, including real Maven builds of the generated code. Run with -Pe2e. */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "orchestrator.llm.mode=replay")
class ScenarioEndToEndTest {

    @Autowired
    private WorkflowEngine engine;

    @Autowired
    private ScenarioCatalog scenarios;

    @ParameterizedTest
    @ValueSource(strings = {"greenfield", "brownfield", "ambiguous"})
    void scenarioCompletesWithAnIntactAuditTrail(String id) throws Exception {
        RunState run = engine.start(scenarios.find(id).orElseThrow(), RunOptions.auto());

        assertThat(engine.awaitCompletion(run.runId(), Duration.ofMinutes(10))).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.audit().verify().valid()).isTrue();
        assertThat(run.outputDir().resolve("change.patch")).isNotEmptyFile();
        if (id.equals("brownfield")) {
            assertThat(run.snapshot().counters()).containsEntry("reworks", 1L);
            assertThat(run.graph().contains("migration-safety")).isTrue();
        }
        if (id.equals("ambiguous")) {
            assertThat(run.clarifications()).containsKeys("Q1", "Q2");
            assertThat(run.graph().contains("threat-model")).isTrue();
        }
    }
}
