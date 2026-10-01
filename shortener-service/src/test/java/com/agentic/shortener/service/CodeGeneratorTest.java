package com.agentic.shortener.service;

import com.agentic.shortener.TestProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator(TestProperties.defaults());

    @Test
    void generatesCodesOfConfiguredLengthFromBase62Alphabet() {
        String code = generator.next();

        assertThat(code).hasSize(7);
        assertThat(code.chars()).allMatch(c -> CodeGenerator.ALPHABET.indexOf(c) >= 0);
    }

    @Test
    void codesAreEffectivelyUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(10_000);
    }
}
