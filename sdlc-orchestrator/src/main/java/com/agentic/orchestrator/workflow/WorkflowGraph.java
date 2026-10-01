package com.agentic.orchestrator.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit dependency graph of stages. Mutable so the run can re-plan (insert stages) while it
 * executes; every mutation is validated and rolled back if it would break the graph.
 * Owned by a single run's scheduler thread; readers get copies.
 */
public final class WorkflowGraph {

    private final Map<String, StageDefinition> stages = new LinkedHashMap<>();

    public WorkflowGraph(Collection<StageDefinition> definitions) {
        for (StageDefinition stage : definitions) {
            if (stages.putIfAbsent(stage.id(), stage) != null) {
                throw new InvalidWorkflowException("Duplicate stage id: " + stage.id());
            }
        }
        validate();
    }

    public WorkflowGraph copy() {
        return new WorkflowGraph(stages.values());
    }

    public StageDefinition stage(String id) {
        StageDefinition stage = stages.get(id);
        if (stage == null) {
            throw new IllegalArgumentException("Unknown stage: " + id);
        }
        return stage;
    }

    public boolean contains(String id) {
        return stages.containsKey(id);
    }

    public Collection<StageDefinition> stages() {
        return List.copyOf(stages.values());
    }

    public Set<String> directDependents(String id) {
        Set<String> result = new LinkedHashSet<>();
        for (StageDefinition stage : stages.values()) {
            if (stage.dependsOn().contains(id)) {
                result.add(stage.id());
            }
        }
        return result;
    }

    /** Every stage that (directly or indirectly) consumes the output of {@code id}, in topological order. */
    public List<String> transitiveDependents(String id) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(directDependents(id));
        while (!queue.isEmpty()) {
            String next = queue.poll();
            if (seen.add(next)) {
                queue.addAll(directDependents(next));
            }
        }
        return topologicalOrder().stream().filter(seen::contains).toList();
    }

    /** Kahn's algorithm; ties keep declaration order so execution order is deterministic. */
    public List<String> topologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        stages.values().forEach(s -> inDegree.put(s.id(), s.dependsOn().size()));
        List<String> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>();
        stages.keySet().stream().filter(id -> inDegree.get(id) == 0).forEach(ready::add);
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(id);
            for (String dependent : directDependents(id)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() != stages.size()) {
            List<String> cyclic = stages.keySet().stream().filter(id -> !order.contains(id)).toList();
            throw new InvalidWorkflowException("Workflow graph has a cycle among: " + cyclic);
        }
        return order;
    }

    public void validate() {
        for (StageDefinition stage : stages.values()) {
            if (stage.dependsOn().contains(stage.id())) {
                throw new InvalidWorkflowException("Stage depends on itself: " + stage.id());
            }
            for (String dep : stage.dependsOn()) {
                if (!stages.containsKey(dep)) {
                    throw new InvalidWorkflowException("Stage " + stage.id() + " depends on unknown stage " + dep);
                }
            }
            if (stage.reworkTarget() != null && !stages.containsKey(stage.reworkTarget())) {
                throw new InvalidWorkflowException("Stage " + stage.id() + " has unknown rework target " + stage.reworkTarget());
            }
        }
        topologicalOrder();
    }

    /**
     * Inserts a stage and makes each of {@code downstream} wait for it. Atomic: an invalid
     * result (cycle, unknown dependency) leaves the graph unchanged.
     */
    public void insert(StageDefinition stage, Set<String> downstream) {
        if (stages.containsKey(stage.id())) {
            throw new InvalidWorkflowException("Stage already exists: " + stage.id());
        }
        Map<String, StageDefinition> before = new LinkedHashMap<>(stages);
        try {
            stages.put(stage.id(), stage);
            for (String id : downstream) {
                stages.put(id, stage(id).withDependency(stage.id()));
            }
            validate();
        } catch (RuntimeException e) {
            stages.clear();
            stages.putAll(before);
            throw e;
        }
    }

    /** Mermaid flowchart for docs and the dashboard, annotated with stage statuses. */
    public String toMermaid(Map<String, String> statuses) {
        StringBuilder out = new StringBuilder("flowchart LR\n");
        for (String id : topologicalOrder()) {
            String status = statuses.getOrDefault(id, "");
            out.append("  ").append(node(id)).append("[\"").append(id)
                    .append(status.isEmpty() ? "" : "<br/>" + status).append("\"]\n");
        }
        for (StageDefinition stage : stages.values()) {
            for (String dep : stage.dependsOn()) {
                out.append("  ").append(node(dep)).append(" --> ").append(node(stage.id())).append('\n');
            }
            if (stage.reworkTarget() != null) {
                out.append("  ").append(node(stage.id())).append(" -. rework .-> ")
                        .append(node(stage.reworkTarget())).append('\n');
            }
        }
        return out.toString();
    }

    private static String node(String id) {
        return id.replace('-', '_');
    }
}
