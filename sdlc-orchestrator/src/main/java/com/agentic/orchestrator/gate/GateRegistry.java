package com.agentic.orchestrator.gate;

import java.util.Map;
import java.util.TreeMap;

public class GateRegistry {

    private final Map<String, Gate> gates = new TreeMap<>();

    public GateRegistry register(String name, Gate gate) {
        gates.put(name, gate);
        return this;
    }

    public Gate get(String name) {
        Gate gate = gates.get(name);
        if (gate == null) {
            throw new IllegalArgumentException("Unknown gate: " + name);
        }
        return gate;
    }

    public java.util.Set<String> names() {
        return gates.keySet();
    }
}
