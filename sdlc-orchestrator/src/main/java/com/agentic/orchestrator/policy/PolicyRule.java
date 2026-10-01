package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.FileChange;

import java.util.List;

/** A change-control rule: looks at one proposed file change. */
public interface PolicyRule {

    String id();

    List<PolicyViolation> check(FileChange change, PolicyContext context);
}
