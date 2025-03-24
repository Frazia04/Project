package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.data.records.UserRecord;
import lombok.Value;

/**
 * An object that stores a (modifiable) {@link UserRecord} along with permission information.
 */
@Value
public class UserWithPermissions {
    UserRecord user;
    boolean isAssistantForAnyExercise;
    boolean isTutorForAnyExercise;
}
