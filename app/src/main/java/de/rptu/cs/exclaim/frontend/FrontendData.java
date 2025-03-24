package de.rptu.cs.exclaim.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.rptu.cs.exclaim.api.FEAccountData;
import de.rptu.cs.exclaim.api.FEExercise;
import de.rptu.cs.exclaim.api.FELanguage;
import de.rptu.cs.exclaim.api.FETerm;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.schema.enums.Term;
import de.rptu.cs.exclaim.security.UserWithPermissions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.springframework.lang.Nullable;

import java.io.IOException;

import static de.rptu.cs.exclaim.utils.JacksonBeans.OBJECT_READER;
import static de.rptu.cs.exclaim.utils.JacksonBeans.OBJECT_WRITER;

/**
 * Collection of helper methods that transform backend data objects into frontend api records.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FrontendData {
    @Contract(value = "null -> null; !null -> !null", pure = true)
    @Nullable
    public static FELanguage mapLanguage(@Nullable String language) {
        try {
            return language == null ? null : OBJECT_READER.readValue("\"" + language + "\"", FELanguage.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid language \"" + language + "\"!", e);
        }
    }

    @Contract(value = "null -> null; !null -> !null", pure = true)
    @Nullable
    public static String mapLanguage(@Nullable FELanguage language) {
        if (language == null) return null;
        try {
            String s = OBJECT_WRITER.writeValueAsString(language);
            return s.substring(1, s.length() - 1);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Contract(value = "null -> null; !null -> !null", pure = true)
    @Nullable
    public static FEAccountData accountData(@Nullable UserWithPermissions userWithPermissions) {
        if (userWithPermissions == null) {
            return null;
        }
        UserRecord user = userWithPermissions.getUser();
        int userId = user.getUserId();
        return new FEAccountData(
            userId,
            user.getFirstname(),
            user.getLastname(),
            user.getEmail(),
            user.getUsername(),
            user.getStudentId(),
            mapLanguage(user.getLanguage()),
            userWithPermissions.getIsTutorForAnyExercise(),
            userWithPermissions.getIsAssistantForAnyExercise(),
            user.getAdmin()
        );
    }

    @Contract(value = "null -> null; !null -> !null", pure = true)
    @Nullable
    public static FETerm.SummerWinter mapTerm(@Nullable Term term) {
        return term == null ? null : switch (term) {
            case SUMMER -> FETerm.SummerWinter.SUMMER;
            case WINTER -> FETerm.SummerWinter.WINTER;
        };
    }

    @Contract(value = "null -> null; !null -> !null", pure = true)
    @Nullable
    public static FEExercise.GroupJoin mapGroupJoin(@Nullable GroupJoin groupJoin) {
        return groupJoin == null ? null : switch (groupJoin) {
            case NONE -> FEExercise.GroupJoin.NONE;
            case GROUP -> FEExercise.GroupJoin.GROUP;
            case PREFERENCES -> FEExercise.GroupJoin.PREFERENCES;
        };
    }
}
