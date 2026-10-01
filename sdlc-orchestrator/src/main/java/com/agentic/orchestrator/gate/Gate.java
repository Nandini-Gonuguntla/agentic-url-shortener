package com.agentic.orchestrator.gate;

@FunctionalInterface
public interface Gate {

    GateResult evaluate(GateContext context);
}
