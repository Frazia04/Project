package de.rptu.cs.exclaim.utils;

import de.rptu.cs.exclaim.data.interfaces.IExercise;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.schema.enums.Term;
import org.springframework.lang.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Comparators {
    private Comparators() {
    }

    /**
     * Compare String identifiers by extracting numerical parts and comparing them numerically.
     * For example, "G7" < "G12".
     */
    public static final Comparator<String> IDENTIFIER = new Comparator<>() {
        private final Pattern splitPattern = Pattern.compile("(?<=\\D)(?=\\d)");
        private final Pattern intPrefixPattern = Pattern.compile("(\\d+)(.*)");

        @Override
        @SuppressWarnings("StringSplitter")
        public int compare(@Nullable String s1, @Nullable String s2) {
            // null is smaller than any String
            if (s1 == null) return s2 == null ? 0 : -1;
            if (s2 == null) return 1;

            // Split the string when a number starts (non-number character is followed by a number character)
            String[] parts1 = splitPattern.split(s1);
            String[] parts2 = splitPattern.split(s2);

            // Compare each part individually
            int i = 0;
            int result = 0;
            for (; result == 0; i++) {
                // Check whether we reached the end of one or both inputs
                if (i == parts1.length) {
                    if (i == parts2.length) {
                        // The identifiers are equal when comparing case-insensitively. To make ordering deterministic
                        // for identifiers that differ only in their case, we do a final case-sensitive comparison.
                        return s1.compareTo(s2);
                    }
                    return -1;
                }
                if (i == parts2.length) {
                    return 1;
                }

                // Compare the current part of both inputs
                String part1 = parts1[i];
                String part2 = parts2[i];
                Matcher m1 = intPrefixPattern.matcher(part1);
                if (m1.matches()) {
                    Matcher m2 = intPrefixPattern.matcher(part2);
                    if (m2.matches()) {
                        // Both parts start with a number -> compare numerically
                        result = Integer.compare(Integer.parseInt(m1.group(1)), Integer.parseInt(m2.group(1)));
                        if (result == 0) {
                            // Equal number -> compare the suffix
                            result = m1.group(2).compareToIgnoreCase(m2.group(2));
                        }
                        continue;
                    }
                }
                result = part1.compareToIgnoreCase(part2); // not both parts start with a number -> compare the strings normally
            }
            return result;
        }
    };

    /**
     * Compare exercises according to their year and term, ordering the latest exercise first.
     */
    public static final Comparator<IExercise> EXERCISE_BY_TERM =
        Comparator.nullsFirst(Comparator
            .comparing(IExercise::getYear)
            .thenComparingInt(e -> Optional.ofNullable(e.getTerm()).map(Term::ordinal).orElse(2)) // semester: summer, winter, null
            .reversed() // latest term first
            .thenComparing(IExercise::getTermComment)
            .thenComparing(IExercise::getExerciseId, IDENTIFIER)
        );

    /**
     * Compare users by name (lastname followed by firstname).
     */
    public static final Comparator<IUser> USER_BY_NAME =
        Comparator.nullsFirst(Comparator
            .comparing(IUser::getLastname)
            .thenComparing(IUser::getFirstname)
            .thenComparing(IUser::getUserId)
        );
}
