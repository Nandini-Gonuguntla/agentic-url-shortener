package com.agentic.shortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "shortener.analytics.async=false",
        "shortener.rate-limit.capacity=2",
        "shortener.rate-limit.refill-per-minute=1"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returns429WithRetryAfterOnceTheBucketIsEmpty() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(create()).andExpect(status().isCreated());
        }

        mvc.perform(create())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()))
                .andExpect(jsonPath("$.type", is("urn:problem:rate-limited")));
    }

    private static org.springframework.test.web.servlet.RequestBuilder create() {
        return post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}");
    }
}
