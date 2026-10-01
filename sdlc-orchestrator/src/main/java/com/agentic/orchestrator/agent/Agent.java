package com.agentic.orchestrator.agent;

import com.agentic.orchestrator.engine.StageOutcome;

/**
 * A unit of autonomous work. Contract: read inputs from the context, return a proposed outcome,
 * never mutate the workspace or run state directly (the engine applies changes after gates pass).
 */
public interface Agent {

    String name();

    StageOutcome execute(AgentContext context) throws Exception;
}
