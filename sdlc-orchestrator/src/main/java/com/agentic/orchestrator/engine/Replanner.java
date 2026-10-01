package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.artifact.DesignDoc;
import com.agentic.orchestrator.artifact.ImpactFlag;
import com.agentic.orchestrator.workflow.WorkflowGraph;
import com.agentic.orchestrator.workflow.WorkflowTemplates;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic re-planning: inspects accepted artifacts and adds the stages they call for.
 * Stages are only ever added (never silently removed) so the plan's evolution stays auditable.
 */
public class Replanner {

    public List<GraphMutation> afterStageAccepted(String stageId, Object artifact, WorkflowGraph graph) {
        List<GraphMutation> mutations = new ArrayList<>();
        if (artifact instanceof DesignDoc design) {
            if (design.has(ImpactFlag.SECURITY_SENSITIVE) && !graph.contains(WorkflowTemplates.THREAT_MODEL)) {
                mutations.add(new GraphMutation(WorkflowTemplates.threatModelStage(),
                        WorkflowTemplates.threatModelDownstream(),
                        "Design is SECURITY_SENSITIVE: threat model required before planning"));
            }
            if (design.has(ImpactFlag.SCHEMA_CHANGE) && !graph.contains(WorkflowTemplates.MIGRATION_SAFETY)) {
                mutations.add(new GraphMutation(WorkflowTemplates.migrationSafetyStage(),
                        WorkflowTemplates.migrationSafetyDownstream(),
                        "Design has SCHEMA_CHANGE: migration safety check required before release"));
            }
        }
        return mutations;
    }
}
