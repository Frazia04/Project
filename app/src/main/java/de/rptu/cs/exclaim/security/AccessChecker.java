package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.data.GroupAndTeam;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.tables.Assistants;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.schema.tables.Tutors;
import de.rptu.cs.exclaim.schema.tables.Users;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

/**
 * Methods to get and check permissions of the currently authenticated user.
 * <p>
 * We load a fresh copy of the currently authenticated user's data for each http request and cache it in a request
 * attribute. This is required to reflect changes that have been made since the user initially authenticated. We thereby
 * check whether the password has been changed. If so, we terminate the current session. A password change therefore
 * terminates all other sessions for the same user.
 * <p>
 * When executing a database query to load user data, we let {@link AutomaticDatabaseTransaction} start a transaction.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccessChecker {
    private static final String REQUEST_ATTRIBUTE_NAME = AccessChecker.class.getName();

    private final DSLContext ctx;
    private final AutomaticDatabaseTransaction automaticDatabaseTransaction;

    /**
     * The currently authenticated user, with permissions (tutor/assistant for any exercise)
     */
    public Optional<UserWithPermissions> getUserWithPermissionsOpt() {
        return getUserHelper(
            (cacheAttribute) -> cacheAttribute instanceof UserWithPermissions userWithPermissions
                ? userWithPermissions
                : null,
            (userId) -> {
                Users u = USERS.as("u");
                Tutors t = TUTORS.as("t");
                Assistants a = ASSISTANTS.as("a");
                return ctx
                    .select(
                        u,
                        DSL.field(DSL.exists(DSL.selectOne().from(a).where(a.USERID.eq(u.USERID)))).as("assistantForAnyExercise"),
                        DSL.field(DSL.exists(DSL.selectOne().from(t).where(t.USERID.eq(u.USERID)))).as("tutorForAnyExercise")
                    )
                    .from(u)
                    .where(u.USERID.eq(userId))
                    .forUpdate()
                    .fetchOne(Records.mapping(UserWithPermissions::new));
            },
            UserWithPermissions::getUser
        );
    }

    /**
     * The currently authenticated user
     */
    public Optional<UserRecord> getUserOpt() {
        return getUserHelper(
            (cacheAttribute) -> cacheAttribute instanceof UserWithPermissions userWithPermissions
                ? userWithPermissions.getUser()
                : (cacheAttribute instanceof UserRecord userRecord ? userRecord : null),
            (userId) -> ctx
                .selectFrom(USERS)
                .where(USERS.USERID.eq(userId))
                .forUpdate()
                .fetchOne(),
            userRecord -> userRecord
        );
    }

    /**
     * Helper method that extracts common logic out of {@link #getUserWithPermissionsOpt} and {@link #getUserOpt()}.
     *
     * @param fromCache   function that extracts the result from the cached request attribute (can return null)
     * @param fromUserId  function that given the userId loads the result from database (can return null)
     * @param extractUser function that extracts the user interface from the database result
     * @return the cached result, if any, otherwise the result from database (after checking that the password hash has
     * not changed)
     */
    private <T> Optional<T> getUserHelper(
        Function<Object, T> fromCache,
        Function<Integer, T> fromUserId,
        Function<T, IUser> extractUser
    ) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        if (securityContext.getAuthentication() instanceof ExclaimAuthentication exclaimAuthentication) {
            // Check for cached result
            HttpServletRequest request = getRequest();
            T resultFromCache = fromCache.apply(request.getAttribute(REQUEST_ATTRIBUTE_NAME));
            if (resultFromCache != null) {
                log.debug("Loaded currently authenticated user from cache: {}", resultFromCache);
                return Optional.of(resultFromCache);
            }

            // Load user data for currently authenticated userId from database
            ExclaimUserPrincipal principal = exclaimAuthentication.getPrincipal();
            int userId = principal.userId;
            log.debug("Loading currently authenticated user id {}", userId);
            automaticDatabaseTransaction.startTransaction(request);
            T resultFromUserId = fromUserId.apply(userId);
            if (resultFromUserId != null) {
                log.debug("Found currently authenticated user in database: {}", resultFromUserId);
                if (Objects.equals(extractUser.apply(resultFromUserId).getPassword(), principal.password)) {
                    request.setAttribute(REQUEST_ATTRIBUTE_NAME, resultFromUserId);
                    return Optional.of(resultFromUserId);
                } else {
                    log.info("Terminating session for user {} because the password has changed", resultFromUserId);
                }
            } else {
                log.info("Terminating session for user id {} because the user does not exist anymore", userId);
            }
            securityContext.setAuthentication(null);
        } else {
            log.debug("Not authenticated");
        }
        return Optional.empty();
    }

    void updateCachedUser(UserWithPermissions userWithPermissions) {
        log.debug("Updating cached user data: {}", userWithPermissions);
        getRequest().setAttribute(REQUEST_ATTRIBUTE_NAME, userWithPermissions);
    }

    /**
     * Update the last known password for the currently authenticated user. Must be called after users change their own
     * password to not invalidate the current session on their next request.
     *
     * @param user the currently authenticated user, with updated password
     */
    public void updatePassword(IUser user) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof ExclaimAuthentication exclaimAuthentication) {
            ExclaimUserPrincipal principal = exclaimAuthentication.getPrincipal();
            if (user.getUserId() != principal.userId) {
                throw new IllegalArgumentException("Provided user is not the currently authenticated user!");
            }
            principal.password = user.getPassword();
        } else {
            throw new IllegalStateException("Cannot set new password if not authenticated");
        }
    }

    /**
     * The user id of the authenticated user, if any. This method does not check the user against the database and
     * therefore does not initiate a database transaction managed via {@link AutomaticDatabaseTransaction}!
     */
    public OptionalInt getUserIdOptUnchecked() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof ExclaimAuthentication exclaimAuthentication
            ? OptionalInt.of(exclaimAuthentication.getPrincipal().userId)
            : OptionalInt.empty();
    }

    /**
     * The user id of the currently authenticated user. This method does not check the user against the database and
     * * therefore does not initiate a database transaction managed via {@link AutomaticDatabaseTransaction}!
     *
     * @throws NotAuthenticatedException if there is no authenticated user
     */
    public int getUserIdUnchecked() {
        return getUserIdOptUnchecked().orElseThrow(NotAuthenticatedException::new);
    }

    /**
     * Whether the current request is authenticated
     */
    public boolean isAuthenticated() {
        return getUserOpt().isPresent();
    }

    /**
     * The currently authenticated user
     *
     * @throws NotAuthenticatedException if there is no authenticated user
     */
    public UserRecord getUser() {
        return getUserOpt().orElseThrow(NotAuthenticatedException::new);
    }

    /**
     * The user id of the currently authenticated user
     *
     * @throws NotAuthenticatedException if there is no authenticated user
     */
    public int getUserId() {
        return getUser().getUserId();
    }

    /**
     * Whether the current request is authenticated and the user has admin permissions
     */
    public boolean isAdmin() {
        log.debug("Checking for admin permissions");
        return getUserOpt()
            .map(UserRecord::getAdmin)
            .orElse(false);
    }

    /**
     * Whether the current request is authenticated and the user has assistant permissions for any exercise
     */
    public boolean isAssistantForAnyExercise() {
        log.debug("Checking for assistant permissions in any exercise");
        return getUserWithPermissionsOpt()
            .map(UserWithPermissions::getIsAssistantForAnyExercise)
            .orElse(false);
    }

    /**
     * Whether the current request is authenticated and the user has assistant permissions for the given exercise
     */
    public boolean isAssistantFor(String exerciseId) {
        int userId = getUserId();
        log.debug("Checking that user {} has assistant permissions for exercise {}", userId, exerciseId);
        return ctx
            .selectOne()
            .from(ASSISTANTS)
            .where(ASSISTANTS.USERID.eq(userId), ASSISTANTS.EXERCISEID.eq(exerciseId))
            .execute() == 1;
    }

    /**
     * Whether the current request is authenticated and the user has tutor permissions for any exercise
     */
    public boolean isTutorForAnyExercise() {
        log.debug("Checking for tutor permissions in any exercise");
        return getUserWithPermissionsOpt()
            .map(UserWithPermissions::getIsTutorForAnyExercise)
            .orElse(false);
    }

    /**
     * The {@link ExerciseRoles} for the currently authenticated user in the given exercise
     *
     * @throws NotAuthenticatedException if there is no authenticated user
     * @throws AccessDeniedException     if the user is not allowed to access the exercise
     */
    public ExerciseRoles getExerciseRoles(String exerciseId) {
        int userId = getUserId();
        log.debug("Fetching exercise roles for user {} in exercise {}", userId, exerciseId);

        Users u = USERS.as("u");
        Students s = STUDENTS.as("s");
        Tutors t = TUTORS.as("t");
        Assistants a = ASSISTANTS.as("a");
        Field<Boolean> isStudent = DSL.field(s.USERID.isNotNull()).as("isStudent");
        Field<Boolean> isTutor = DSL.field(t.USERID.isNotNull()).as("isTutor");
        Field<Boolean> isAssistant = DSL.field(a.USERID.isNotNull()).as("isAssistant");
        Field<String> studentGroup = s.GROUPID.as("studentGroup");
        Field<String> studentTeam = s.TEAMID.as("studentTeam");
        Field<String[]> tutorGroups = DSL.arrayAgg(t.GROUPID).as("tutorGroups");
        return ctx
            .select(isStudent, isTutor, isAssistant, studentGroup, studentTeam, tutorGroups)
            .from(u)
            .leftJoin(s).onKey(Keys.FK__STUDENTS__USERS).and(s.EXERCISEID.eq(exerciseId))
            .leftJoin(t).onKey(Keys.FK__TUTORS__USERS).and(t.EXERCISEID.eq(exerciseId))
            .leftJoin(a).onKey(Keys.FK__ASSISTANTS__USERS).and(a.EXERCISEID.eq(exerciseId))
            .where(u.USERID.eq(userId))
            .groupBy(u)
            .fetchOptional(r ->
                // isStudent || isTutor || isAssistant
                r.value1() || r.value2() || r.value3() ?
                    new ExerciseRoles(
                        // groupAndTeam
                        r.value1() ? new GroupAndTeam(r.value4(), r.value5()) : null,
                        // tutorGroups
                        r.value2() ? Set.of(r.value6()) : null,
                        // isAssistant
                        r.value3()
                    )
                    // empty result if no permissions
                    : null
            )
            .orElseThrow(() -> new AccessDeniedException("User " + userId + " is not allowed to access exercise " + exerciseId));
    }

    private static HttpServletRequest getRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        throw new IllegalStateException("Could not get current request!");
    }
}
