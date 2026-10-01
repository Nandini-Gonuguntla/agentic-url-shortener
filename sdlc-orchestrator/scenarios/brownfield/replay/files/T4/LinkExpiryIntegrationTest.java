package com.agentic.shortener.api;

import com.agentic.shortener.TestProperties.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "shortener.analytics.async=false",
        "shortener.rate-limit.capacity=1000"
})
@AutoConfigureMockMvc
@Import(LinkExpiryIntegrationTest.ControllableClock.class)
class LinkExpiryIntegrationTest {

    @TestConfiguration
    static class ControllableClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ObjectMapper json;

    @Test
    void expiringLinkRedirectsUntilExpiryThenReturns410EvenWhenCached() throws Exception {
        Instant createdAt = clock.instant();
        String code = create("{\"url\":\"https://example.com/flash-sale\",\"expiresInSeconds\":60}");

        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt", is(createdAt.plusSeconds(60).toString())));
        mvc.perform(get("/" + code)).andExpect(status().isFound()); // warms the redirect cache

        clock.advance(Duration.ofSeconds(61));

        mvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type", is("urn:problem:link-expired")));
        mvc.perform(get("/api/v1/links/" + code)).andExpect(status().isOk()); // metadata stays available
    }

    @Test
    void linksWithoutExpiryNeverExpire() throws Exception {
        String code = create("{\"url\":\"https://example.com/forever\"}");

        clock.advance(Duration.ofDays(3650));

        mvc.perform(get("/" + code)).andExpect(status().isFound());
        JsonNode metadata = json.readTree(mvc.perform(get("/api/v1/links/" + code))
                .andReturn().getResponse().getContentAsString());
        assertThat(metadata.path("expiresAt").isNull() || metadata.path("expiresAt").isMissingNode()).isTrue();
    }

    @Test
    void rejectsOutOfRangeExpiry() throws Exception {
        for (String ttl : new String[]{"0", "-5", "315360001"}) {
            mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com\",\"expiresInSeconds\":" + ttl + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type", is("urn:problem:invalid-request")));
        }
    }

    private String create(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("code").asText();
    }
}
