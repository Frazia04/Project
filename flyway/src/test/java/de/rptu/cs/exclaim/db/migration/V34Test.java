package de.rptu.cs.exclaim.db.migration;

import de.rptu.cs.exclaim.db.TestUtils;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V34Test {
    private static final String OLD_VERSION = "33";
    private static final String NEW_VERSION = "34.2";

    @Test
    void testMigration() throws Exception {
        TestUtils.testMigration(OLD_VERSION, NEW_VERSION,
            ctx -> {
                // Add data in the old format
                ctx.batch("INSERT INTO exercises (id, lecture, term, group_join) VALUES (?, 'My lecture', ?, 'NONE')")
                    // id, term
                    .bind("E1", "Sommersemester 19")
                    .bind("E2", "Sommersemester 20")
                    .bind("E3", "Sommersemester 21")
                    .bind("E4", "Sommersemester 2019")
                    .bind("E5", "Sommersemester 2020")
                    .bind("E6", "Sommersemester 2021")
                    .bind("E11", "SS 19")
                    .bind("E12", "SS 20")
                    .bind("E13", "SS 21")
                    .bind("E14", "SS 2019")
                    .bind("E15", "SS 2020")
                    .bind("E16", "SS 2021")
                    .bind("E21", "Wintersemester 19/20")
                    .bind("E22", "Wintersemester 20/21")
                    .bind("E23", "Wintersemester 21/22")
                    .bind("E24", "Wintersemester 2019/20")
                    .bind("E25", "Wintersemester 2020/21")
                    .bind("E26", "Wintersemester 2021/22")
                    .bind("E27", "Wintersemester 2019/2020")
                    .bind("E28", "Wintersemester 2020/2021")
                    .bind("E29", "Wintersemester 2021/2022")
                    .bind("E31", "WS 19/20")
                    .bind("E32", "WS 20/21")
                    .bind("E33", "WS 21/22")
                    .bind("E34", "WS 2019/20")
                    .bind("E35", "WS 2020/21")
                    .bind("E36", "WS 2021/22")
                    .bind("E37", "WS 2019/2020")
                    .bind("E38", "WS 2020/2021")
                    .bind("E39", "WS 2021/2022")
                    .bind("E100", "Some invalid text")
                    .bind("E101", "WS 2020/22")
                    .execute();
            },
            ctx -> {
                // Fetch the updated data.
                // We use a dedicated class for easier result comparison and error message formatting.
                @Value
                class Exercise {
                    String id;
                    int year;
                    @Nullable String term;
                    String termComment;
                }
                List<Exercise> result = ctx
                    .resultQuery("SELECT id, \"year\", term, term_comment FROM exercises")
                    .fetch(r -> new Exercise(
                        r.get(0, String.class),
                        r.get(1, Integer.class),
                        r.get(2, String.class),
                        r.get(3, String.class)
                    ));

                // Sort the result such that we can use list-based comparison for the assertion
                result.sort(Comparator.comparing(e -> Integer.parseInt(e.id.substring(1))));

                // Compare with expected result
                assertEquals(List.of(
                    new Exercise("E1", 2019, "SUMMER", ""),
                    new Exercise("E2", 2020, "SUMMER", ""),
                    new Exercise("E3", 2021, "SUMMER", ""),
                    new Exercise("E4", 2019, "SUMMER", ""),
                    new Exercise("E5", 2020, "SUMMER", ""),
                    new Exercise("E6", 2021, "SUMMER", ""),
                    new Exercise("E11", 2019, "SUMMER", ""),
                    new Exercise("E12", 2020, "SUMMER", ""),
                    new Exercise("E13", 2021, "SUMMER", ""),
                    new Exercise("E14", 2019, "SUMMER", ""),
                    new Exercise("E15", 2020, "SUMMER", ""),
                    new Exercise("E16", 2021, "SUMMER", ""),
                    new Exercise("E21", 2019, "WINTER", ""),
                    new Exercise("E22", 2020, "WINTER", ""),
                    new Exercise("E23", 2021, "WINTER", ""),
                    new Exercise("E24", 2019, "WINTER", ""),
                    new Exercise("E25", 2020, "WINTER", ""),
                    new Exercise("E26", 2021, "WINTER", ""),
                    new Exercise("E27", 2019, "WINTER", ""),
                    new Exercise("E28", 2020, "WINTER", ""),
                    new Exercise("E29", 2021, "WINTER", ""),
                    new Exercise("E31", 2019, "WINTER", ""),
                    new Exercise("E32", 2020, "WINTER", ""),
                    new Exercise("E33", 2021, "WINTER", ""),
                    new Exercise("E34", 2019, "WINTER", ""),
                    new Exercise("E35", 2020, "WINTER", ""),
                    new Exercise("E36", 2021, "WINTER", ""),
                    new Exercise("E37", 2019, "WINTER", ""),
                    new Exercise("E38", 2020, "WINTER", ""),
                    new Exercise("E39", 2021, "WINTER", ""),
                    new Exercise("E100", 0, null, "Some invalid text"),
                    new Exercise("E101", 0, null, "WS 2020/22")
                ), result);
            });
    }
}
