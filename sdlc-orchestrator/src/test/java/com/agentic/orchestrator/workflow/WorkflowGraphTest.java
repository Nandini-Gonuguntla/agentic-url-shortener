package com.agentic.orchestrator.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphTest {

    private static StageDefinition s(String id, String... deps) {
        return StageDefinition.builder(id, "agent").dependsOn(deps).build();
    }

    @Test
    void topologicalOrderRespectsDependenciesAndDeclarationOrder() {
        WorkflowGraph graph = new WorkflowGraph(List.of(s("a"), s("b", "a"), s("c", "a"), s("d", "b", "c")));

        assertThat(graph.topologicalOrder()).containsExactly("a", "b", "c", "d");
        assertThat(graph.transitiveDependents("a")).containsExactly("b", "c", "d");
        assertThat(graph.directDependents("b")).containsExactly("d");
    }

    @Test
    void rejectsCyclesUnknownDependenciesAndDuplicates() {
        assertThatThrownBy(() -> new WorkflowGraph(List.of(s("a", "b"), s("b", "a"))))
                .isInstanceOf(InvalidWorkflowException.class).hasMessageContaining("cycle");
        assertThatThrownBy(() -> new WorkflowGraph(List.of(s("a", "missing"))))
                .isInstanceOf(InvalidWorkflowException.class).hasMessageContaining("unknown");
        assertThatThrownBy(() -> new WorkflowGraph(List.of(s("a"), s("a"))))
                .isInstanceOf(InvalidWorkflowException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void insertRewiresDownstreamStages() {
        WorkflowGraph graph = new WorkflowGraph(List.of(s("design"), s("plan", "design")));

        graph.insert(s("threat", "design"), Set.of("plan"));

        assertThat(graph.topologicalOrder()).containsExactly("design", "threat", "plan");
        assertThat(graph.stage("plan").dependsOn()).contains("threat");
    }

    @Test
    void invalidInsertLeavesTheGraphUnchanged() {
        WorkflowGraph graph = new WorkflowGraph(List.of(s("a"), s("b", "a")));

        assertThatThrownBy(() -> graph.insert(s("x", "b"), Set.of("a"))).isInstanceOf(InvalidWorkflowException.class);

        assertThat(graph.contains("x")).isFalse();
        assertThat(graph.stage("a").dependsOn()).isEmpty();
    }

    @Test
    void standardSdlcIsValidAndHasTheExpectedFanOut() {
        WorkflowGraph graph = WorkflowTemplates.standardSdlc();

        assertThat(graph.directDependents(WorkflowTemplates.IMPLEMENTATION)).containsExactlyInAnyOrder(
                WorkflowTemplates.VALIDATION, WorkflowTemplates.SECURITY_REVIEW, WorkflowTemplates.DOCUMENTATION);
        assertThat(graph.stage(WorkflowTemplates.RELEASE_READINESS).approval()).isEqualTo(ApprovalMode.ALWAYS);
        assertThat(graph.toMermaid(Map.of())).contains("validation -. rework .-> implementation");
    }
}
