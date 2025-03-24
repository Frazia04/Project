package de.rptu.cs.exclaim;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;

@Component
@Slf4j
public final class Study {
    public static final String STUDY_EXERCISE = "GdP23";
    public static final Set<String> STUDY_GROUPS_TOOLS = Set.of("02", "05", "07", "10");
    public static final Set<String> STUDY_GROUPS_GOALS = Set.of("01", "04", "08", "09");
    private final DSLContext ctx;

    private volatile Set<Integer> toolsUsers;
    private volatile Set<Integer> goalsUsers;

    public Study(DSLContext ctx) {
        this.ctx = ctx;
        this.reloadUsers();
    }

    public boolean isToolsUser(int userId) {
        log.debug("Checking whether userId {} is tools user", userId);
        return toolsUsers.contains(userId);
    }

    public boolean isGoalsUser(int userId) {
        log.debug("Checking whether userId {} is goals user", userId);
        return goalsUsers.contains(userId);
    }

    @Scheduled(initialDelay = 3, fixedDelay = 3, timeUnit = TimeUnit.MINUTES)
    public void reloadUsers() {
        log.debug("Reloading study users...");
        this.toolsUsers = ctx
            .select(STUDENTS.USERID)
            .from(STUDENTS)
            .where(
                STUDENTS.EXERCISEID.eq(STUDY_EXERCISE)/*,
                STUDENTS.GROUPID.in(STUDY_GROUPS_TOOLS)*/
            )
            .union(DSL
                .select(TUTORS.USERID)
                .from(TUTORS)
                .where(TUTORS.EXERCISEID.eq(STUDY_EXERCISE))
            )
            .union(DSL
                .select(ASSISTANTS.USERID)
                .from(ASSISTANTS)
                .where(ASSISTANTS.EXERCISEID.eq(STUDY_EXERCISE))
            )
            .fetchSet(Record1::component1);
        this.goalsUsers = ctx
            .select(STUDENTS.USERID)
            .from(STUDENTS)
            .where(
                STUDENTS.EXERCISEID.eq(STUDY_EXERCISE)/*,
                STUDENTS.GROUPID.in(STUDY_GROUPS_GOALS)*/
            )
            .fetchSet(Record1::component1);
    }
}
