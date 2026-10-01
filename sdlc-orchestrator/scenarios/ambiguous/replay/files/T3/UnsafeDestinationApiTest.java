package com.agentic.shortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "shortener.analytics.async=false",
        "shortener.rate-limit.capacity=1000"
})
@AutoConfigureMockMvc
class UnsafeDestinationApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void refusesBlockedDomainsWith422() throws Exception {
        create("https://login.phishing.test/account")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", is("urn:problem:unsafe-destination")));
    }

    @Test
    void refusesIpLiteralHosts() throws Exception {
        create("http://203.0.113.7/download")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", is("urn:problem:unsafe-destination")));
    }

    @Test
    void refusesEmbeddedCredentialsWithoutEchoingThem() throws Exception {
        create("https://user:hunter2@example.com/")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", not(containsString("hunter2"))));
    }

    @Test
    void ordinaryUrlsAreStillAccepted() throws Exception {
        create("https://www.example.com/spring-sale").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions create(String url) throws Exception {
        return mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"));
    }
}
