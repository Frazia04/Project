package de.rptu.cs.exclaim;

import org.jooq.TableField;
import org.junit.jupiter.api.Test;

import static de.rptu.cs.exclaim.schema.tables.Assignments.ASSIGNMENTS;
import static de.rptu.cs.exclaim.schema.tables.Examgrades.EXAMGRADES;
import static de.rptu.cs.exclaim.schema.tables.Exams.EXAMS;
import static de.rptu.cs.exclaim.schema.tables.Examtasks.EXAMTASKS;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Groups.GROUPS;
import static de.rptu.cs.exclaim.schema.tables.Sheets.SHEETS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test suite checks that the compile-time constants declared in {@link ExclaimValidationProperties} are suitable
 * for the database schema, i.e. that maximum length restrictions do not exceed the database column size.
 */
public class ExclaimValidationPropertiesTest {
    private static void checkMaxLength(String propertyName, TableField<?, String> tableField) throws ReflectiveOperationException {
        int propertyValue = (int) ExclaimValidationProperties.class.getField(propertyName).get(null);
        int databaseLength = tableField.getDataType().length();
        if (propertyValue > databaseLength) {
            fail(String.format("%s is %s, but the database schema only supports a length up to %s.", propertyName, propertyValue, databaseLength));
        }
    }

    @Test
    void testUsernameMaxLength() throws ReflectiveOperationException {
        checkMaxLength("USERNAME_LENGTH_MAX", USERS.USERNAME);
    }

    @Test
    void testFirstnameMaxLength() throws ReflectiveOperationException {
        checkMaxLength("FIRSTNAME_LENGTH_MAX", USERS.FIRSTNAME);
    }

    @Test
    void testLastnameMaxLength() throws ReflectiveOperationException {
        checkMaxLength("LASTNAME_LENGTH_MAX", USERS.LASTNAME);
    }

    @Test
    void testEmailMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EMAIL_LENGTH_MAX", USERS.EMAIL);
    }

    @Test
    void testExerciseIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXERCISE_ID_LENGTH_MAX", EXERCISES.ID);
    }

    @Test
    void testExerciseLectureMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXERCISE_LECTURE_LENGTH_MAX", EXERCISES.LECTURE);
    }

    @Test
    void testExerciseTermCommentMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXERCISE_TERM_COMMENT_LENGTH_MAX", EXERCISES.TERM_COMMENT);
    }

    @Test
    void testGroupIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("GROUP_ID_LENGTH_MAX", GROUPS.GROUPID);
    }

    @Test
    void testGroupTimeMaxLength() throws ReflectiveOperationException {
        checkMaxLength("GROUP_TIME_LENGTH_MAX", GROUPS.TIME);
    }

    @Test
    void testGroupLocationMaxLength() throws ReflectiveOperationException {
        checkMaxLength("GROUP_LOCATION_LENGTH_MAX", GROUPS.LOCATION);
    }

    @Test
    void testSheetIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("SHEET_ID_LENGTH_MAX", SHEETS.ID);
    }

    @Test
    void testSheetLabelMaxLength() throws ReflectiveOperationException {
        checkMaxLength("SHEET_LABEL_LENGTH_MAX", SHEETS.LABEL);
    }

    @Test
    void testAssignmentIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("ASSIGNMENT_ID_LENGTH_MAX", ASSIGNMENTS.ID);
    }

    @Test
    void testAssignmentLabelMaxLength() throws ReflectiveOperationException {
        checkMaxLength("ASSIGNMENT_LABEL_LENGTH_MAX", ASSIGNMENTS.LABEL);
    }

    @Test
    void testExamIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXAM_ID_LENGTH_MAX", EXAMS.ID);
    }

    @Test
    void testExamLabelMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXAM_LABEL_LENGTH_MAX", EXAMS.LABEL);
    }

    @Test
    void testExamLocationMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXAM_LOCATION_LENGTH_MAX", EXAMS.LOCATION);
    }

    @Test
    void testExamTaskIdMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXAM_TASK_ID_LENGTH_MAX", EXAMTASKS.ID);
    }

    @Test
    void testExamGradeMaxLength() throws ReflectiveOperationException {
        checkMaxLength("EXAM_GRADE_LENGTH_MAX", EXAMGRADES.GRADE);
    }
}
