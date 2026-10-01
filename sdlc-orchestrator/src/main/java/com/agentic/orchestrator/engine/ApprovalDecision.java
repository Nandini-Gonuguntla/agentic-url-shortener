package com.agentic.orchestrator.engine;

/**
 * @param reworkStage optional upstream stage to send the work back to on REJECT; defaults to the
 *                    stage's configured rework target, or the stage itself
 */
public record ApprovalDecision(ApprovalVerdict verdict, String approver, String comment, String reworkStage) {
}
