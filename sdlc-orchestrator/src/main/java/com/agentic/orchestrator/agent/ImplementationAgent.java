package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.ChangeSet;
import com.agentic.orchestrator.artifact.CodebaseAnalysis;
import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.FileChange;
import com.agentic.orchestrator.artifact.ImplementationResult;
import com.agentic.orchestrator.artifact.RequirementSpec;
import com.agentic.orchestrator.artifact.TaskChangeSet;
import com.agentic.orchestrator.artifact.TaskKind;
import com.agentic.orchestrator.artifact.TaskPlan;
import com.agentic.orchestrator.artifact.ThreatModel;
import com.agentic.orchestrator.engine.StageOutcome;
import com.agentic.orchestrator.llm.LlmResponse;
import com.agentic.orchestrator.workflow.StageDefinition;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import com.agentic.orchestrator.workflow.WorkflowTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-step execution: walks the plan's task DAG in dependency order and makes one model call
 * per task. Each task sees the files as already changed by earlier tasks (an in-memory overlay),
 * so later steps build on earlier ones without touching the real workspace.
 */
public class ImplementationAgent extends LlmAgent<TaskChangeSet> {

    public ImplementationAgent(ObjectMapper json) {
        super(json);
    }

    @Override
    public String name() {
        return "implementation";
    }

    @Override
    protected Class<TaskChangeSet> outputType() {
        return TaskChangeSet.class;
    }

    @Override
    protected long maxTokens() {
        return 32_000;
    }

    @Override
    protected String instructions() {
        return """
                Role: senior Java engineer implementing ONE task of an approved plan.
                - Return complete file contents for every file you create or modify (no diffs, no placeholders).
                - Only touch files needed for this task; keep the existing style, packages and error handling.
                - Production-quality code: validation, clear naming, no dead code, no secrets, no logging of personal data.
                - Tests use JUnit 5 + AssertJ (+ MockMvc for API tests) and must be deterministic.
                - Schema changes are new Flyway files (next free version); never edit existing migrations.
                - If feedback lists failing tests or policy violations, fix the root cause in this task's files.
                """;
    }

    @Override
    protected String prompt(AgentContext ctx) {
        throw new UnsupportedOperationException("built per task");
    }

    @Override
    public StageOutcome execute(AgentContext ctx) {
        RequirementSpec spec = ctx.require(WorkflowTemplates.REQUIREMENTS, RequirementSpec.class);
        DesignDoc design = ctx.require(WorkflowTemplates.DESIGN, DesignDoc.class);
        TaskPlan plan = ctx.require(WorkflowTemplates.PLAN, TaskPlan.class);
        CodebaseAnalysis analysis = ctx.require(WorkflowTemplates.ANALYSIS, CodebaseAnalysis.class);
        String threats = ctx.optional(WorkflowTemplates.THREAT_MODEL, ThreatModel.class).map(this::render).orElse(null);

        Map<String, String> overlay = new HashMap<>();
        List<TaskChangeSet> results = new ArrayList<>();
        Set<String> producers = new LinkedHashSet<>();
        for (TaskPlan.PlannedTask task : ordered(plan)) {
            if (task.kind() == TaskKind.DOCS) {
                continue;
            }
            Set<String> context = new LinkedHashSet<>(task.files() == null ? List.of() : task.files());
            design.components().forEach(c -> context.addAll(c.files() == null ? List.of() : c.files()));
            String prompt = section("Task " + task.id(), render(task))
                    + section("Requirement summary and acceptance criteria", spec.summary() + "\n" + render(spec.acceptanceCriteria()))
                    + section("Approved design", render(design))
                    + (threats == null ? "" : section("Threat model mitigations", threats))
                    + section("Completed tasks so far", results.isEmpty() ? "(none)"
                    : String.join("\n", results.stream().map(r -> "- " + r.taskId() + ": " + r.summary()).toList()))
                    + section("Project files", String.join("\n", analysis.inventory().sourceFiles()))
                    + section("Current content of relevant files", FileContext.render(context, overlay, ctx.workspace()));
            LlmResponse<TaskChangeSet> response = call(ctx, name() + "." + task.id(), prompt, TaskChangeSet.class);
            producers.add(producedBy(response));
            TaskChangeSet change = normalize(task.id(), response.value());
            for (FileChange file : change.changes()) {
                overlay.put(file.path(), file.action() == ChangeAction.DELETE ? null : file.content());
            }
            results.add(change);
            ctx.telemetry().event(name(), "TASK_COMPLETED", Map.of("task", task.id(), "title", task.title(),
                    "files", change.changes().stream().map(c -> c.action() + " " + c.path()).toList()));
        }
        return StageOutcome.success(new ImplementationResult(results), new ChangeSet(results), String.join(", ", producers));
    }

    /** Topological order of the plan's tasks (the plan gate already proved the graph is acyclic). */
    static List<TaskPlan.PlannedTask> ordered(TaskPlan plan) {
        Map<String, TaskPlan.PlannedTask> byId = new HashMap<>();
        List<StageDefinition> nodes = new ArrayList<>();
        for (TaskPlan.PlannedTask task : plan.tasks()) {
            byId.put(task.id(), task);
            nodes.add(StageDefinition.builder(task.id(), "task")
                    .dependsOn(task.dependsOn() == null ? new String[0] : task.dependsOn().toArray(String[]::new)).build());
        }
        return new WorkflowGraph(nodes).topologicalOrder().stream().map(byId::get).toList();
    }

    private static TaskChangeSet normalize(String taskId, TaskChangeSet change) {
        List<FileChange> files = change.changes() == null ? List.of() : change.changes().stream()
                .map(f -> new FileChange(f.path() == null ? "" : f.path().strip().replace('\\', '/'), f.action(), f.content()))
                .toList();
        return new TaskChangeSet(taskId, change.summary() == null ? taskId : change.summary(), files, change.notes());
    }
}
