package com.agentic.shortener.service;

import com.agentic.shortener.config.SafetyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationSafetyPolicyTest {

    private final DestinationSafetyPolicy policy =
            new DestinationSafetyPolicy(new SafetyProperties(List.of("phishing.test", "Malware.Test."), true));

    @ParameterizedTest
    @ValueSource(strings = {"https://phishing.test/login", "https://login.phishing.test/", "https://A.B.PHISHING.TEST/x",
            "https://phishing.test./trailing-dot", "http://malware.test"})
    void rejectsBlockedDomainsAndSubdomains(String url) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOf(UnsafeDestinationException.class)
                .hasMessageContaining("blocked");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://notphishing.test/", "https://phishing.test.example.com/", "https://example.com/phishing.test"})
    void doesNotBlockLookalikeSuffixesOrPaths(String url) {
        assertThatCode(() -> policy.check(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.0.1/", "http://[::1]/", "http://3232235521/", "http://0xc0a80001/"})
    void rejectsIpLiteralHosts(String url) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOf(UnsafeDestinationException.class)
                .hasMessageContaining("IP address");
    }

    @Test
    void ipLiteralsCanBeAllowedByConfiguration() {
        DestinationSafetyPolicy permissive = new DestinationSafetyPolicy(new SafetyProperties(List.of(), false));

        assertThatCode(() -> permissive.check("http://192.168.0.1/")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://bank.example@evil.example/", "https://user:secret@example.com/"})
    void rejectsEmbeddedCredentialsWithoutEchoingThem(String url) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOf(UnsafeDestinationException.class)
                .hasMessageContaining("credentials")
                .hasMessageNotContaining("secret");
    }

    @Test
    void allowsOrdinaryUrls() {
        assertThatCode(() -> policy.check("https://www.example.com/path?q=1")).doesNotThrowAnyException();
    }
}
