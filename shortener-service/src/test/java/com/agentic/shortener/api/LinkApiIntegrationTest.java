package com.agentic.shortener.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class LinkApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void createResolveAndReportStats() throws Exception {
        String code = createLink("https://example.com/landing", null);

        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl", is("https://example.com/landing")))
                .andExpect(jsonPath("$.shortUrl", is("http://localhost:8080/" + code)));

        mvc.perform(get("/" + code).header("Referer", "https://news.example.org/post?id=1")
                        .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/" + code).with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                .andExpect(status().isFound());
        mvc.perform(get("/" + code).with(r -> { r.setRemoteAddr("10.0.0.2"); return r; }))
                .andExpect(status().isFound());

        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks", is(3)))
                .andExpect(jsonPath("$.uniqueVisitors", is(2)))
                .andExpect(jsonPath("$.clicksByDay[0].clicks", is(3)))
                .andExpect(jsonPath("$.topReferrers[0].host", is("news.example.org")));
    }

    @Test
    void customAliasIsUsedAndConflictsAreRejected() throws Exception {
        String code = createLink("https://example.com", "spring-sale");

        org.assertj.core.api.Assertions.assertThat(code).isEqualTo("spring-sale");
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://other.example.com\",\"customAlias\":\"spring-sale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("urn:problem:alias-taken")));
    }

    @Test
    void rejectsInvalidInputWithProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type", is("urn:problem:invalid-url")));

        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("urn:problem:invalid-request")));

        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"customAlias\":\"api\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("urn:problem:invalid-alias")));
    }

    @Test
    void unknownCodesReturn404() throws Exception {
        mvc.perform(get("/zzzzzzz")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("urn:problem:link-not-found")));
        mvc.perform(get("/api/v1/links/zzzzzzz/stats")).andExpect(status().isNotFound());
    }

    @Test
    void deletedLinksStopResolvingEvenWhenCached() throws Exception {
        String code = createLink("https://example.com/temp", null);
        mvc.perform(get("/" + code)).andExpect(status().isFound()); // warms the cache

        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());

        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNotFound());
    }

    private String createLink(String url, String alias) throws Exception {
        String body = alias == null
                ? "{\"url\":\"%s\"}".formatted(url)
                : "{\"url\":\"%s\",\"customAlias\":\"%s\"}".formatted(url, alias);
        String response = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/links/")))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("code").asText();
    }
}
