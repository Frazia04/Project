package de.rptu.cs.exclaim.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class RandomTokenGeneratorTest {
    @Test
    void testGenerate() {
        RandomTokenGenerator generator = new RandomTokenGenerator();
        String token = generator.generate();
        if (!token.matches("[a-z0-9]{50}")) {
            fail(String.format("The generated token \"%s\" does not match the required regex pattern.", token));
        }
    }
}
