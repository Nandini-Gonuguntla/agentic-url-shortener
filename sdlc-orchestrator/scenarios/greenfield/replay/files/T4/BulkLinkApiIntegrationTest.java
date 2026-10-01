package com.agentic.shortener.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "shortener.analytics.async=false",
        "shortener.rate-limit.capacity=1000"
})
@AutoConfigureMockMvc
class BulkLinkApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void mixedBatchReturnsPerItemResultsInOrder() throws Exception {
        String body = """
                {"items": [
                  {"url": "https://example.com/one"},
                  {"url": "ftp://example.com/bad"},
                  {"url": "https://example.com/two", "customAlias": "bulk-promo"},
                  {"url": "https://example.com/three", "customAlias": "bulk-promo"}
                ]}""";

        String response = mvc.perform(post("/api/v1/links/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(4)))
                .andExpect(jsonPath("$.created", is(2)))
                .andExpect(jsonPath("$.failed", is(2)))
                .andExpect(jsonPath("$.results[0].index", is(0)))
                .andExpect(jsonPath("$.results[0].status", is("CREATED")))
                .andExpect(jsonPath("$.results[1].status", is("REJECTED")))
                .andExpect(jsonPath("$.results[1].problemType", is("urn:problem:invalid-url")))
                .andExpect(jsonPath("$.results[2].status", is("CREATED")))
                .andExpect(jsonPath("$.results[2].link.code", is("bulk-promo")))
                .andExpect(jsonPath("$.results[3].status", is("REJECTED")))
                .andExpect(jsonPath("$.results[3].problemType", is("urn:problem:alias-taken")))
                .andReturn().getResponse().getContentAsString();

        JsonNode first = json.readTree(response).get("results").get(0).get("link");
        mvc.perform(get("/" + first.get("code").asText()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/one"));
    }

    @Test
    void emptyBatchIsRejected() throws Exception {
        mvc.perform(post("/api/v1/links/bulk").contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("urn:problem:invalid-request")));
        mvc.perform(post("/api/v1/links/bulk").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedBatchIsRejected() throws Exception {
        String items = IntStream.range(0, 101)
                .mapToObj(i -> "{\"url\": \"https://example.com/" + i + "\"}")
                .collect(Collectors.joining(","));

        mvc.perform(post("/api/v1/links/bulk").contentType(MediaType.APPLICATION_JSON).content("{\"items\": [" + items + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allItemsFailingIsStillAProcessedBatch() throws Exception {
        String response = mvc.perform(post("/api/v1/links/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"url\": \"not-a-url\"}, {\"url\": \"\"}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = json.readTree(response);
        assertThat(node.get("created").asInt()).isZero();
        assertThat(node.get("failed").asInt()).isEqualTo(2);
    }
}
