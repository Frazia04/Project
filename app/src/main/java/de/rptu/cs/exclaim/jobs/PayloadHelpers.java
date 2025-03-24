package de.rptu.cs.exclaim.jobs;

import org.springframework.lang.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Helpers to convert some common types to byte[] payloads and back.
 */
class PayloadHelpers {
    // int/long:

    static int payloadToInt(@Nullable byte[] payload) {
        try {
            return new BigInteger(Objects.requireNonNull(payload, "Payload must not be null")).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid payload", e);
        }
    }

    static long payloadToLong(@Nullable byte[] payload) {
        try {
            return new BigInteger(Objects.requireNonNull(payload, "Payload must not be null")).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid payload", e);
        }
    }

    static byte[] longToPayload(long value) {
        return BigInteger.valueOf(value).toByteArray();
    }


    // Strings:

    static String payloadToString(@Nullable byte[] payload) {
        return new String(Objects.requireNonNull(payload, "Payload must not be null"), StandardCharsets.UTF_8);
    }

    static byte[] stringToPayload(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
