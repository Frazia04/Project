package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.data.GroupAndTeam;
import lombok.Value;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.lang.Nullable;

import java.util.Set;
import java.util.function.Supplier;

@Value
public class ExerciseRoles {
    public ExerciseRoles(@Nullable GroupAndTeam groupAndTeam, @Nullable Set<String> tutorGroups, boolean isAssistant) {
        this.groupAndTeam = groupAndTeam;
        this.tutorGroups = tutorGroups == null || tutorGroups.isEmpty() ? null : tutorGroups;
        this.isAssistant = isAssistant;
    }

    /**
     * The group and team information if the user has a student role, otherwise null.
     */
    @Nullable
    GroupAndTeam groupAndTeam;

    /**
     * A non-empty set of groups for which the user is a tutor, or null if the user is not a tutor.
     */
    @Nullable
    Set<String> tutorGroups;

    boolean isAssistant;

    public boolean isStudent() {
        return groupAndTeam != null;
    }

    public boolean canAssess() {
        return isAssistant || tutorGroups != null;
    }

    public boolean canAssess(String groupId) {
        return isAssistant || (tutorGroups != null && tutorGroups.contains(groupId));
    }

    public Condition applyGroupIdRestriction(Field<String> groupIdField) {
        return isAssistant ? DSL.noCondition() : groupIdField.in(tutorGroups);
    }

    public Condition applyGroupIdRestriction(Supplier<Field<String>> groupIdFieldSupplier) {
        return isAssistant ? DSL.noCondition() : groupIdFieldSupplier.get().in(tutorGroups);
    }
}
