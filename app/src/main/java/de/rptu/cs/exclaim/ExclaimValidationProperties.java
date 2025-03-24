package de.rptu.cs.exclaim;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Some constants to be used in validation annotations.
 * <p>
 * Due to the restrictions of the validation API, the parameters (like {@code max} in {@link Size} or {@code regexp}
 * in {@link Pattern}) must be compile-time constants and cannot be loaded from properties files.
 * <p>
 * Therefore, some additional environment-specific validation properties (e.g. student id) are loaded dynamically, see
 * {@link ExclaimProperties.Validation}. Those cannot be used in validation annotations, a manual check is required.
 */
public class ExclaimValidationProperties {
    // Identifier that is used for primary keys in the database and in file/folder names
    public static final String ID_REGEX = "[a-zA-Z0-9_-]*";

    // -----------------------------------------------------------------------------------------------------------------
    // User Data

    // Username
    public static final String USERNAME_REGEX = "[a-zA-Z0-9_-]*[a-zA-Z][a-zA-Z0-9_-]*";
    public static final int USERNAME_LENGTH_MIN = 3;
    public static final int USERNAME_LENGTH_MAX = 50;

    // First and last name
    public static final int FIRSTNAME_LENGTH_MAX = 50;
    public static final int LASTNAME_LENGTH_MAX = 50;

    // Password
    public static final int PASSWORD_LENGTH_MIN = 8;

    // E-Mail
    public static final int EMAIL_LENGTH_MAX = 200;

    // -----------------------------------------------------------------------------------------------------------------
    // Exercise data
    public static final int EXERCISE_ID_LENGTH_MAX = 50;
    public static final int EXERCISE_LECTURE_LENGTH_MAX = 250;
    public static final int EXERCISE_TERM_COMMENT_LENGTH_MAX = 255;

    // -----------------------------------------------------------------------------------------------------------------
    // Group data
    public static final int GROUP_ID_LENGTH_MAX = 50;
    public static final int GROUP_TIME_LENGTH_MAX = 200;
    public static final int GROUP_LOCATION_LENGTH_MAX = 200;

    // -----------------------------------------------------------------------------------------------------------------
    // Sheet data
    public static final int SHEET_ID_LENGTH_MAX = 50;
    public static final int SHEET_LABEL_LENGTH_MAX = 200;

    // -----------------------------------------------------------------------------------------------------------------
    // Assignment data
    public static final int ASSIGNMENT_ID_LENGTH_MAX = 50;
    public static final int ASSIGNMENT_LABEL_LENGTH_MAX = 200;

    // -----------------------------------------------------------------------------------------------------------------
    // Exam data
    public static final int EXAM_ID_LENGTH_MAX = 50;
    public static final int EXAM_LABEL_LENGTH_MAX = 200;
    public static final int EXAM_LOCATION_LENGTH_MAX = 200;

    // -----------------------------------------------------------------------------------------------------------------
    // Exam task data
    public static final int EXAM_TASK_ID_LENGTH_MAX = 50;

    // -----------------------------------------------------------------------------------------------------------------
    // Exam grade data
    public static final int EXAM_GRADE_LENGTH_MAX = 50;
}
