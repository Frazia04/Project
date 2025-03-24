package de.rptu.cs.exclaim.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class RandomTokenGenerator {
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 50;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] result = new char[LENGTH];
        for (int i = 0; i < result.length; i++) {
            result[i] = CHARS.charAt(random.nextInt(CHARS.length()));
        }
        return new String(result);
    }
}
