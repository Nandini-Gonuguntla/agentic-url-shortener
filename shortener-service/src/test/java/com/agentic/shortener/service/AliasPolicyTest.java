package com.agentic.shortener.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasPolicyTest {

    private final AliasPolicy policy = new AliasPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"abc", "my-link", "Promo_2026", "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"})
    void acceptsValidAliases(String alias) {
        assertThatCode(() -> policy.validate(alias)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "slash/es", "dot.ted", "thirty-three-characters-long-xxxxx"})
    void rejectsMalformedAliases(String alias) {
        assertThatThrownBy(() -> policy.validate(alias)).isInstanceOf(InvalidAliasException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "actuator", "health"})
    void rejectsReservedAliasesCaseInsensitively(String alias) {
        assertThatThrownBy(() -> policy.validate(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("reserved");
    }
}
