package com.agentic.orchestrator.policy;

import com.agentic.orchestrator.artifact.ChangeAction;
import com.agentic.orchestrator.artifact.FileChange;

import java.util.List;

/** A rule that inspects file content line by line; reused by the security review over final files. */
public interface ContentRule extends PolicyRule {

    List<PolicyViolation> scan(String path, String content);

    @Override
    default List<PolicyViolation> check(FileChange change, PolicyContext context) {
        if (change.action() == ChangeAction.DELETE || change.content() == null) {
            return List.of();
        }
        return scan(change.path(), change.content());
    }
}
