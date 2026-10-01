package com.agentic.orchestrator.agent;

import java.util.Map;
import java.util.TreeMap;

public class AgentRegistry {

    private final Map<String, Agent> agents = new TreeMap<>();

    public AgentRegistry register(Agent agent) {
        agents.put(agent.name(), agent);
        return this;
    }

    public Agent get(String name) {
        Agent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalArgumentException("No agent registered as " + name);
        }
        return agent;
    }

    public java.util.Set<String> names() {
        return agents.keySet();
    }
}
