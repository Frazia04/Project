package de.rptu.cs.exclaim.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ComparatorsIdentifierTest {
    @ParameterizedTest
    @MethodSource("identifiers")
    void testAsc(String id1, String id2) {
        int result = Comparators.IDENTIFIER.compare(id1, id2);
        if (result >= 0) {
            fail(String.format("IDENTIFIER.compare(\"%s\", \"%s\") = %d, but should be < 0.", id1, id2, result));
        }
    }

    @ParameterizedTest
    @MethodSource("identifiers")
    void testDesc(String id1, String id2) {
        int result = Comparators.IDENTIFIER.compare(id2, id1);
        if (result <= 0) {
            fail(String.format("IDENTIFIER.compare(\"%s\", \"%s\") = %d, but should be > 0.", id2, id1, result));
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"01", "G01", "G1", "foo", "bar", "", " "})
    void testEq(String id) {
        assertEquals(0, Comparators.IDENTIFIER.compare(id, id), String.format("Wrong result for IDENTIFIER.compare(\"%s\", \"%s\")", id, id));
    }

    /**
     * Provide a Stream of Arguments, each consisting of two String identifiers, where the first identifier is
     * considered strictly smaller than the second one.
     *
     * @return the provided arguments
     */
    static Stream<Arguments> identifiers() {
        return Stream.of(
            arguments("1", "2"),
            arguments("2", "10"),
            arguments("19", "20"),
            arguments("G1", "G2"),
            arguments("G2", "G10"),
            arguments("G19", "G20"),
            arguments("G1-1", "G1-2"),
            arguments("G1-2", "G1-10"),
            arguments("G1-19", "G1-20"),
            arguments("G1-0", "G2-0"),
            arguments("G2-0", "G10-0"),
            arguments("G19-0", "G20-0"),
            arguments("G3-4", "G10-0"),
            arguments("G3-T4", "G10-T0"),
            arguments("bar", "foo"),
            arguments("1bar", "bar"),
            arguments("foo", "foo1bar"),
            arguments("ABC", "abd"),
            arguments("abc", "ABD"),
            arguments("ABC", "abc"),
            arguments(null, ""),
            arguments(null, "foo")
        );
    }
}
