package com.agentic.orchestrator.artifact;

/** Impact classes that drive approval requirements and dynamic re-planning. */
public enum ImpactFlag {
    SCHEMA_CHANGE, PUBLIC_API_CHANGE, SECURITY_SENSITIVE, CONFIG_CHANGE, DEPENDENCY_CHANGE, DATA_PRIVACY
}
