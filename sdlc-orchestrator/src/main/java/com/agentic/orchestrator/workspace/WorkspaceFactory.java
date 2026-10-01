package com.agentic.orchestrator.workspace;

import java.nio.file.Path;

public interface WorkspaceFactory {

    Workspace create(Path target);
}
