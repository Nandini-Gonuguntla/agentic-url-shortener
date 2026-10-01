package com.agentic.orchestrator.llm;

import com.agentic.orchestrator.artifact.TaskChangeSet;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayLlmClientTest {

    @TempDir
    Path tmp;

    @Test
    void prefersPerExecutionFixturesAndResolvesFileReferences() throws Exception {
        Path replay = Files.createDirectories(tmp.resolve("demo/replay/files"));
        Files.writeString(replay.resolve("A.java"), "class A {}");
        Files.writeString(tmp.resolve("demo/replay/implementation.T1.json"),
                "{\"taskId\":\"T1\",\"summary\":\"final\",\"changes\":[{\"path\":\"A.java\",\"action\":\"CREATE\",\"content\":\"@file:files/A.java\"}]}");
        Files.writeString(tmp.resolve("demo/replay/implementation.T1.attempt-1.json"),
                "{\"taskId\":\"T1\",\"summary\":\"first try\",\"changes\":[]}");
        ReplayLlmClient client = new ReplayLlmClient(tmp, JsonMapper.builder().findAndAddModules().build());

        TaskChangeSet first = client.generate(request(1), TaskChangeSet.class).value();
        TaskChangeSet second = client.generate(request(2), TaskChangeSet.class).value();

        assertThat(first.summary()).isEqualTo("first try");
        assertThat(second.summary()).isEqualTo("final");
        assertThat(second.changes().getFirst().content()).isEqualTo("class A {}");
    }

    @Test
    void missingFixturesFailClearlyAndRefsCannotEscape() throws Exception {
        ReplayLlmClient client = new ReplayLlmClient(tmp, JsonMapper.builder().build());
        assertThatThrownBy(() -> client.generate(request(1), TaskChangeSet.class))
                .isInstanceOf(LlmException.class).hasMessageContaining("live mode");

        Files.createDirectories(tmp.resolve("demo/replay"));
        Files.writeString(tmp.resolve("demo/replay/implementation.T1.json"), "{\"summary\":\"@file:../../../secret.txt\"}");
        assertThatThrownBy(() -> client.generate(request(1), TaskChangeSet.class)).isInstanceOf(LlmException.class);
    }

    @Test
    void routingFallsBackToReplayAndMarksTheResponseDegraded() throws Exception {
        Files.createDirectories(tmp.resolve("demo/replay"));
        Files.writeString(tmp.resolve("demo/replay/implementation.T1.json"), "{\"taskId\":\"T1\",\"summary\":\"rec\",\"changes\":[]}");
        LlmClient down = new LlmClient() {
            @Override
            public <T> LlmResponse<T> generate(LlmRequest request, Class<T> type) {
                throw new LlmException("503 overloaded");
            }

            @Override
            public String mode() {
                return "live";
            }
        };
        RoutingLlmClient routing = new RoutingLlmClient(down, new ReplayLlmClient(tmp, JsonMapper.builder().build()));

        LlmResponse<TaskChangeSet> response = routing.generate(request(1), TaskChangeSet.class);

        assertThat(response.degraded()).isTrue();
        assertThat(response.note()).contains("503");
        assertThat(routing.mode()).isEqualTo("live+replay-fallback");
    }

    private static LlmRequest request(int execution) {
        return new LlmRequest("implementation", "implementation.T1", execution, "demo", "sys", "prompt", 1000);
    }
}
