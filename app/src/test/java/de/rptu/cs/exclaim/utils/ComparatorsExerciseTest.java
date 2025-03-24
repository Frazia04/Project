package de.rptu.cs.exclaim.utils;

import de.rptu.cs.exclaim.data.interfaces.IExercise;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.schema.enums.Term;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ComparatorsExerciseTest {
    @ParameterizedTest
    @MethodSource("exercises")
    void testAsc(IExercise e1, IExercise e2) {
        int result = Comparators.EXERCISE_BY_TERM.compare(e1, e2);
        if (result >= 0) {
            fail(String.format("EXERCISE_BY_TERM.compare(%s, %s) = %d, but should be < 0.", e1, e2, result));
        }
    }

    @ParameterizedTest
    @MethodSource("exercises")
    void testDesc(IExercise e1, IExercise e2) {
        int result = Comparators.EXERCISE_BY_TERM.compare(e2, e1);
        if (result <= 0) {
            fail(String.format("EXERCISE_BY_TERM.compare(%s, %s) = %d, but should be > 0.", e2, e1, result));
        }
    }

    @ParameterizedTest
    @MethodSource("exercise")
    void testEq(IExercise e) {
        assertEquals(0, Comparators.EXERCISE_BY_TERM.compare(e, e), String.format("Wrong result for EXERCISE_BY_TERM.compare(%s, %s)", e, e));
    }

    /**
     * Provide a Stream of Arguments, each consisting of two exercises, where the first one is considered strictly
     * smaller than the second one.
     *
     * @return the provided arguments
     */
    static Stream<Arguments> exercises() {
        return Stream.of(
            arguments(
                new TestExercise("Test", (short) 2019, Term.WINTER, ""),
                new TestExercise("Test", (short) 2019, Term.SUMMER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, Term.SUMMER, ""),
                new TestExercise("Test", (short) 2019, Term.WINTER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, Term.SUMMER, ""),
                new TestExercise("Test", (short) 2019, Term.SUMMER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, Term.WINTER, ""),
                new TestExercise("Test", (short) 2019, Term.WINTER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, null, ""),
                new TestExercise("Test", (short) 2020, Term.WINTER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, null, ""),
                new TestExercise("Test", (short) 2020, Term.SUMMER, "")
            ),
            arguments(
                new TestExercise("Test", (short) 2020, null, "bar"),
                new TestExercise("Test", (short) 2020, null, "foo")
            ),
            arguments(
                new TestExercise("Bar", (short) 2020, null, ""),
                new TestExercise("Foo", (short) 2020, null, "")
            ),
            arguments(
                new TestExercise("L5", (short) 2020, null, ""),
                new TestExercise("L10", (short) 2020, null, "")
            ),
            arguments(
                null,
                new TestExercise("Test", (short) 2020, Term.SUMMER, "")
            )
        );
    }

    /**
     * Provide a Set of exercises, collected from the {@link #exercises()} result.
     *
     * @return the provided exercises
     */
    static Set<IExercise> exercise() {
        return exercises()
            .flatMap(args -> Arrays.stream(args.get()))
            .map(e -> (IExercise) e)
            .collect(Collectors.toSet());
    }

    @Value
    @RequiredArgsConstructor
    @SuppressWarnings("MissingOverride")
    private static class TestExercise implements IExercise {
        String exerciseId;
        Short year;
        @Nullable
        Term term;
        String termComment;

        @Override
        public String getLecture() {
            return "FooLecture";
        }

        @Override
        public Boolean getRegistrationOpen() {
            return true;
        }

        @Override
        public GroupJoin getGroupJoin() {
            return GroupJoin.NONE;
        }
    }
}
