package com.agentic.orchestrator.gate;

import com.agentic.orchestrator.engine.Question;

import java.util.List;

public record GateResult(String gate, Verdict verdict, List<String> reasons, List<Question> questions) {

    public enum Verdict { PASS, FAIL, NEEDS_APPROVAL, NEEDS_INPUT }

    public static GateResult pass(String gate) {
        return new GateResult(gate, Verdict.PASS, List.of(), List.of());
    }

    public static GateResult pass(String gate, List<String> notes) {
        return new GateResult(gate, Verdict.PASS, List.copyOf(notes), List.of());
    }

    public static GateResult fail(String gate, String... reasons) {
        return new GateResult(gate, Verdict.FAIL, List.of(reasons), List.of());
    }

    public static GateResult fail(String gate, List<String> reasons) {
        return new GateResult(gate, Verdict.FAIL, List.copyOf(reasons), List.of());
    }

    public static GateResult needsApproval(String gate, List<String> reasons) {
        return new GateResult(gate, Verdict.NEEDS_APPROVAL, List.copyOf(reasons), List.of());
    }

    public static GateResult needsInput(String gate, List<Question> questions) {
        return new GateResult(gate, Verdict.NEEDS_INPUT,
                questions.stream().map(q -> q.id() + ": " + q.question()).toList(), List.copyOf(questions));
    }
}
