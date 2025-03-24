package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import de.rptu.cs.exclaim.data.AssignmentWithTestInfo;
import de.rptu.cs.exclaim.data.ExamWithRegistered;
import de.rptu.cs.exclaim.data.GroupAndTeam;
import de.rptu.cs.exclaim.data.GroupWithCurrentSizeAndTutors;
import de.rptu.cs.exclaim.data.NonNullGroupAndTeam;
import de.rptu.cs.exclaim.data.PreviewFileType;
import de.rptu.cs.exclaim.data.SheetWithResult;
import de.rptu.cs.exclaim.data.TeamResultData3;
import de.rptu.cs.exclaim.data.TestResultDetails;
import de.rptu.cs.exclaim.data.interfaces.IAssignment;
import de.rptu.cs.exclaim.data.interfaces.IGroup;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.AnnotationRecord;
import de.rptu.cs.exclaim.data.records.GroupPreferenceRecord;
import de.rptu.cs.exclaim.data.records.GroupRecord;
import de.rptu.cs.exclaim.data.records.SheetRecord;
import de.rptu.cs.exclaim.data.records.StudentResultRecord;
import de.rptu.cs.exclaim.data.records.TeamPreferenceRecord;
import de.rptu.cs.exclaim.data.records.TeamResultRecord;
import de.rptu.cs.exclaim.data.records.TestResultRecord;
import de.rptu.cs.exclaim.data.records.UploadRecord;
import de.rptu.cs.exclaim.data.records.WarningRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.jobs.BackgroundJobExecutor;
import de.rptu.cs.exclaim.jobs.RunTest;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.enums.Attendance;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.schema.enums.GroupPreferenceOption;
import de.rptu.cs.exclaim.schema.tables.Assignments;
import de.rptu.cs.exclaim.schema.tables.Examparticipants;
import de.rptu.cs.exclaim.schema.tables.Exams;
import de.rptu.cs.exclaim.schema.tables.Groups;
import de.rptu.cs.exclaim.schema.tables.Sheets;
import de.rptu.cs.exclaim.schema.tables.Studentresults;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.schema.tables.Teampreferences;
import de.rptu.cs.exclaim.schema.tables.Teamresults;
import de.rptu.cs.exclaim.schema.tables.TeamresultsAssignment;
import de.rptu.cs.exclaim.schema.tables.Testresult;
import de.rptu.cs.exclaim.schema.tables.Tutors;
import de.rptu.cs.exclaim.schema.tables.Unread;
import de.rptu.cs.exclaim.schema.tables.Uploads;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExerciseRoles;
import de.rptu.cs.exclaim.utils.Comparators;
import de.rptu.cs.exclaim.utils.Markdown;
import de.rptu.cs.exclaim.utils.RteServices;
import de.rptu.cs.exclaim.utils.UploadManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record6;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.jooq.lambda.function.Consumer3;
import org.jooq.lambda.function.Consumer4;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Admissions.ADMISSIONS;
import static de.rptu.cs.exclaim.schema.tables.Annotations.ANNOTATIONS;
import static de.rptu.cs.exclaim.schema.tables.Assignments.ASSIGNMENTS;
import static de.rptu.cs.exclaim.schema.tables.Examparticipants.EXAMPARTICIPANTS;
import static de.rptu.cs.exclaim.schema.tables.Exams.EXAMS;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Grouppreferences.GROUPPREFERENCES;
import static de.rptu.cs.exclaim.schema.tables.Groups.GROUPS;
import static de.rptu.cs.exclaim.schema.tables.Sheets.SHEETS;
import static de.rptu.cs.exclaim.schema.tables.Studentresults.STUDENTRESULTS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Teampreferences.TEAMPREFERENCES;
import static de.rptu.cs.exclaim.schema.tables.Teamresults.TEAMRESULTS;
import static de.rptu.cs.exclaim.schema.tables.TeamresultsAssignment.TEAMRESULTS_ASSIGNMENT;
import static de.rptu.cs.exclaim.schema.tables.Testresult.TESTRESULT;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;
import static de.rptu.cs.exclaim.schema.tables.Unread.UNREAD;
import static de.rptu.cs.exclaim.schema.tables.Uploads.UPLOADS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static de.rptu.cs.exclaim.schema.tables.Warnings.WARNINGS;
import static de.rptu.cs.exclaim.utils.UploadManager.INTERNAL_DTF;

@Controller
@RequestMapping("/exercise/{exerciseId}")
@RequiredArgsConstructor
@Slf4j
public class ExerciseController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final DSLContext ctx;
    private final AccessChecker accessChecker;
    private final UploadManager uploadManager;
    private final ExclaimProperties exclaimProperties;
    private final RunTest runTest;
    private final BackgroundJobExecutor backgroundJobExecutor;
    private final RteServices rteServices;
    private final SimpMessagingTemplate broker;

    @ModelAttribute
    public ExerciseRoles exerciseRoles(@PathVariable String exerciseId) {
        ExerciseRoles exerciseRoles = accessChecker.getExerciseRoles(exerciseId);
        log.debug("Accessing exercise {} with {}", exerciseId, exerciseRoles);
        return exerciseRoles;
    }

    @GetMapping("")
    public String getExerciseOverviewPage(@PathVariable String exerciseId, ExerciseRoles exerciseRoles, Model model) {
        metricsService.registerAccess();
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        if (groupAndTeam != null) {
            // Retrieve admission information
            int userId = accessChecker.getUserId();
            ctx.fetchOptional(ADMISSIONS,
                ADMISSIONS.EXERCISE.eq(exerciseId),
                ADMISSIONS.USERID.eq(userId)
            ).ifPresent(
                admission -> model.addAttribute("admission", admission)
            );

            // Collect team members if the student is assigned to a team
            String groupId = groupAndTeam.getGroupId();
            String teamId = groupAndTeam.getTeamId();
            if (groupId != null && teamId != null) {
                Students s = STUDENTS.as("s");
                Users u = s.user().as("u");
                model.addAttribute(
                    "teamMembers",
                    ctx
                        .select(u)
                        .from(s)
                        .where(s.EXERCISEID.eq(exerciseId), s.GROUPID.eq(groupId), s.TEAMID.eq(teamId))
                        .fetch(Record1::value1)
                );
            }

            // Collect all sheets with results
            Sheets s = SHEETS.as("s");
            Assignments a = ASSIGNMENTS.as("a");
            Studentresults sr = STUDENTRESULTS.as("sr");
            Teamresults tr = TEAMRESULTS.as("tr");
            TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
            Unread u = Unread.UNREAD.as("u");
            Uploads upl = u.upload().as("upl");
            List<SheetWithResult> sheets = ctx
                .select(
                    s,
                    DSL.sum(a.MAXPOINTS).as("maxpoints"),
                    DSL.if_(
                        // Do not consider any points when hidden
                        DSL.condition(DSL.ifnull(tr.HIDEPOINTS, false)), DSL.val((BigDecimal) null),
                        // Sum of team and delta points. If everything is null, then null.
                        DSL.coalesce(DSL.sum(tra.POINTS).plus(DSL.ifnull(sr.DELTAPOINTS, BigDecimal.ZERO)), sr.DELTAPOINTS)
                    ).as("points"),
                    sr.ATTENDED,
                    DSL.field(DSL.and(
                        DSL.not(DSL.condition(DSL.ifnull(tr.HIDECOMMENTS, false))),
                        DSL.exists(DSL
                            .selectOne()
                            .from(u)
                            .where(upl.EXERCISE.eq(s.EXERCISE), upl.SHEET.eq(s.ID), u.USERID.eq(userId))
                        )
                    )).as("unread")
                )
                .from(s)
                .leftJoin(a).onKey(Keys.FK__ASSIGNMENTS__SHEETS)
                .leftJoin(sr).onKey(Keys.FK__STUDENTRESULTS__SHEETS).and(sr.USERID.eq(userId))
                .leftJoin(tr).onKey(Keys.FK__TEAMRESULTS__SHEETS).and(tr.GROUPID.eq(sr.GROUPID)).and(tr.TEAMID.eq(sr.TEAMID))
                .leftJoin(tra).onKey(Keys.FK__TEAMRESULTS_ASSIGNMENT__TEAMRESULTS).and(tra.ASSIGNMENT.eq(a.ID))
                .where(s.EXERCISE.eq(exerciseId))
                .groupBy(s)
                .fetch(Records.mapping(SheetWithResult::new));
            sheets.sort(Comparator.comparing(SheetWithResult::getSheetId, Comparators.IDENTIFIER));
            BigDecimal maxPointsTotal = BigDecimal.ZERO;
            BigDecimal maxPointsGraded = BigDecimal.ZERO;
            BigDecimal achievedPoints = BigDecimal.ZERO;
            int totalAbsent = 0;
            for (SheetWithResult sheet : sheets) {
                BigDecimal maxPoints = sheet.getMaxPoints();
                BigDecimal points = sheet.getPoints();
                maxPointsTotal = maxPointsTotal.add(maxPoints);
                if (points != null) {
                    maxPointsGraded = maxPointsGraded.add(maxPoints);
                    achievedPoints = achievedPoints.add(points);
                }
                if (sheet.getAttended() == Attendance.ABSENT) {
                    totalAbsent++;
                }
            }
            model.addAttribute("sheets", sheets);
            model.addAttribute("maxPointsTotal", maxPointsTotal);
            model.addAttribute("maxPointsGraded", maxPointsGraded);
            model.addAttribute("maxPointsUngraded", maxPointsTotal.subtract(maxPointsGraded));
            model.addAttribute("achievedPoints", achievedPoints);
            model.addAttribute("totalAbsent", totalAbsent);

            // Collect exams with information whether the student is registered
            Exams e = EXAMS.as("e");
            Examparticipants ep = EXAMPARTICIPANTS.as("ep");
            Field<Boolean> registered = DSL.field(ep.USERID.isNotNull()).as("registered");
            model.addAttribute(
                "exams",
                ctx
                    .select(e, registered)
                    .from(e)
                    .leftJoin(ep).onKey(Keys.FK__EXAMPARTICIPANTS__EXAMS).and(ep.USERID.eq(userId))
                    .where(e.EXERCISE.eq(exerciseId))
                    .fetch(Records.mapping(ExamWithRegistered::new))
            );
        } else {
            // Not a student: Collect sheet and exams without student-specific data
            List<SheetRecord> sheets = ctx.fetch(SHEETS, SHEETS.EXERCISE.eq(exerciseId));
            sheets.sort(Comparator.comparing(SheetRecord::getSheetId, Comparators.IDENTIFIER));
            model.addAttribute("sheets", sheets);
            model.addAttribute("exams", ctx.fetch(EXAMS, EXAMS.EXERCISE.eq(exerciseId)));
        }
        return "exercise/overview";
    }

    @GetMapping("/groups")
    public String getExerciseGroupsPage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        model.addAttribute("exercise", ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new));

        Groups g = GROUPS.as("g");
        Students s = STUDENTS.as("s");
        Tutors t = TUTORS.as("t");
        Users u = t.user().as("u");
        Field<Integer> currentSize = DSL.field(DSL
            .select(DSL.count(s.USERID))
            .from(s)
            .where(s.EXERCISEID.eq(g.EXERCISEID), s.GROUPID.eq(g.GROUPID))
        ).as("currentSize");
        Field<List<IUser>> tutors = DSL
            .multisetAgg(u)
            .orderBy(u.LASTNAME, u.FIRSTNAME)
            .as("tutors")
            .convertFrom(result ->
                // no tutors -> a single record with all null values is produced by multisetAgg
                (result.size() == 1 && result.get(0).value1().getUserId() == null)
                    ? Collections.emptyList()
                    : result.map(Record1::value1)
            );
        List<GroupWithCurrentSizeAndTutors> groups = ctx
            .select(g, currentSize, tutors)
            .from(g)
            .leftJoin(t).onKey(Keys.FK__TUTORS__GROUPS)
            .where(g.EXERCISEID.eq(exerciseId))
            .groupBy(g)
            .fetch(Records.mapping(GroupWithCurrentSizeAndTutors::new));
        groups.sort(Comparator.comparing(IGroup::getGroupId, Comparators.IDENTIFIER));
        model.addAttribute("groups", groups);
        return "exercise/groups";
    }

    @PostMapping("/groups/{groupId}/join")
    public String join(@PathVariable String exerciseId, @PathVariable String groupId, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        if (groupAndTeam != null && groupId != null && groupAndTeam.getGroupId() == null
            && ctx
            .select(EXERCISES.GROUP_JOIN)
            .from(EXERCISES)
            .where(EXERCISES.ID.eq(exerciseId))
            .fetchSingle(Record1::value1) == GroupJoin.GROUP
        ) {
            // Check for capacity when joining a group
            var maxSize = DSL
                .select(GROUPS.MAX_SIZE)
                .from(GROUPS)
                .where(GROUPS.EXERCISEID.eq(exerciseId), GROUPS.GROUPID.eq(groupId));
            var currentSize = DSL
                .select(DSL.count(STUDENTS.USERID))
                .from(STUDENTS)
                .where(STUDENTS.EXERCISEID.eq(exerciseId), STUDENTS.GROUPID.eq(groupId));
            // Check that currentSize < maxSize. If maxSize is NULL (i.e. no capacity limit), then the whole expression
            // evaluates to NULL, and we need to fall back to TRUE. Our currentSize expression cannot be NULL.
            // => IFNULL(currentSize < maxSize, TRUE)
            Condition capacityCondition = DSL.condition(DSL.ifnull(DSL.field(currentSize.lt(maxSize)), true));

            if (ctx
                .update(STUDENTS)
                .set(STUDENTS.GROUPID, groupId)
                .where(STUDENTS.USERID.eq(accessChecker.getUserId()), STUDENTS.EXERCISEID.eq(exerciseId), capacityCondition)
                .execute() == 1
            ) {
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("exercise.group-join-success"), redirectAttributes);
            } else {
                addRedirectMessage(MessageType.ERROR, msg.getMessage("exercise.group-join-failed"), redirectAttributes);
            }
        } else {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("exercise.group-join-failed"), redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/groups";
    }

    @PostMapping("/groups/{groupId}/leave")
    public String leave(@PathVariable String exerciseId, @PathVariable String groupId, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        // TODO: Do consistency checks directly in SQL
        if (groupAndTeam != null && groupId != null && groupId.equals(groupAndTeam.getGroupId()) && groupAndTeam.getTeamId() == null
            && ctx
            .select(EXERCISES.GROUP_JOIN)
            .from(EXERCISES)
            .where(EXERCISES.ID.eq(exerciseId))
            .fetchSingle(Record1::value1) == GroupJoin.GROUP
            && ctx
            .update(STUDENTS)
            .set(STUDENTS.GROUPID, (String) null)
            .where(STUDENTS.USERID.eq(accessChecker.getUserId()), STUDENTS.EXERCISEID.eq(exerciseId))
            .execute() == 1
        ) {
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("exercise.group-leave-success"), redirectAttributes);
        } else {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("exercise.group-leave-failed"), redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/groups";
    }

    @GetMapping("/groups/preferences")
    public String getPreferencesPage(@PathVariable String exerciseId, Model model, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!exerciseRoles.isStudent()
            || ctx
            .select(EXERCISES.GROUP_JOIN)
            .from(EXERCISES)
            .where(EXERCISES.ID.eq(exerciseId))
            .fetchSingle(Record1::value1) != GroupJoin.PREFERENCES
        ) {
            addRedirectMessage(MessageType.ERROR, "Die Angabe von Präferenzen ist nicht (mehr) möglich.", redirectAttributes);
            return "redirect:/exercise/{exerciseId}/groups";
        }
        int userId = accessChecker.getUserId();
        List<GroupRecord> groups = ctx.fetch(GROUPS, GROUPS.EXERCISEID.eq(exerciseId));
        groups.sort(Comparator.comparing(IGroup::getGroupId, Comparators.IDENTIFIER));
        model.addAttribute("groups", groups);
        model.addAttribute(
            "groupPreferences",
            ctx
                .select(GROUPPREFERENCES.GROUPID, GROUPPREFERENCES.PREFERENCE)
                .from(GROUPPREFERENCES)
                .where(GROUPPREFERENCES.EXERCISEID.eq(exerciseId), GROUPPREFERENCES.USERID.eq(userId))
                .fetchMap(Record2::value1, Record2::value2)
        );

        Teampreferences tp = TEAMPREFERENCES.as("tp");
        Users u = USERS.as("u");
        List<String> friendUsernames = ctx
            .select(DSL.ifnull(u.USERNAME, u.USERID.cast(String.class)))
            .from(tp)
            .innerJoin(u).on(u.USERID.eq(tp.FRIEND_USERID))
            .where(tp.EXERCISEID.eq(exerciseId), tp.USERID.eq(userId))
            .fetch(Record1::value1);
        Collections.sort(friendUsernames);
        model.addAttribute("friendUsernames", friendUsernames);
        return "exercise/preferences";
    }

    @PostMapping("/groups/preferences")
    public String savePreferences(@PathVariable String exerciseId, @RequestBody MultiValueMap<String, String> formData, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!exerciseRoles.isStudent()
            || ctx
            .select(EXERCISES.GROUP_JOIN)
            .from(EXERCISES)
            .where(EXERCISES.ID.eq(exerciseId))
            .fetchSingle(Record1::value1) != GroupJoin.PREFERENCES
        ) {
            addRedirectMessage(MessageType.ERROR, "Die Angabe von Präferenzen ist nicht (mehr) möglich.", redirectAttributes);
            return "redirect:/exercise/{exerciseId}/groups";
        }
        int userId = accessChecker.getUserId();

        // Save group preferences
        Map<String, GroupPreferenceOption> groupPreferences = new HashMap<>();
        ctx
            .select(GROUPS.GROUPID)
            .from(GROUPS)
            .where(GROUPS.EXERCISEID.eq(exerciseId))
            .forEach(r -> {
                String groupId = r.value1();
                String p = formData.getFirst("group-" + groupId);
                if (p != null) {
                    try {
                        groupPreferences.put(groupId, GroupPreferenceOption.valueOf(p));
                    } catch (IllegalArgumentException e) {
                        // ignore invalid enum name
                    }
                }
            });
        if (!groupPreferences.isEmpty()) {
            // TODO: Rewrite using MERGE query
            Map<String, GroupPreferenceRecord> existingRecords = ctx
                .selectFrom(GROUPPREFERENCES)
                .where(GROUPPREFERENCES.EXERCISEID.eq(exerciseId), GROUPPREFERENCES.USERID.eq(userId))
                .forUpdate()
                .fetchMap(GroupPreferenceRecord::getGroupId);
            List<GroupPreferenceRecord> recordsToStore = new ArrayList<>(groupPreferences.size());
            groupPreferences.forEach((groupId, preference) -> {
                GroupPreferenceRecord record = existingRecords.get(groupId);
                if (record != null) {
                    if (record.getPreference() != preference) {
                        record.setPreference(preference);
                        recordsToStore.add(record);
                    }
                } else {
                    record = ctx.newRecord(GROUPPREFERENCES);
                    record.setExerciseId(exerciseId);
                    record.setUserId(userId);
                    record.setGroupId(groupId);
                    record.setPreference(preference);
                    recordsToStore.add(record);
                }
            });
            if (!recordsToStore.isEmpty()) {
                ctx.batchStore(recordsToStore).execute();
            }
        }

        // Save team preferences
        List<String> friendUsernames = new ArrayList<>(4);
        for (int i = 1; i <= 4; i++) {
            String f = formData.getFirst("friend-" + i);
            if (StringUtils.isNotEmpty(f)) {
                friendUsernames.add(f);
            }
        }

        // Check for existing friends
        Teampreferences tp = TEAMPREFERENCES.as("tp");
        Users u = USERS.as("u");
        List<Record2<Integer, String>> existingFriends = ctx
            .select(u.USERID, u.USERNAME)
            .from(tp)
            .innerJoin(u).on(u.USERID.eq(tp.FRIEND_USERID))
            .where(tp.EXERCISEID.eq(exerciseId), tp.USERID.eq(userId))
            .forUpdate()
            .fetch();

        // Compare new with existing
        Set<String> friendUsernamesToAdd = new LinkedHashSet<>(friendUsernames);
        List<Integer> friendsToDelete = new ArrayList<>(4);
        for (Record2<Integer, String> r : existingFriends) {
            int friendUserId = r.value1();
            String friendUsername = r.value2();
            if (friendUsername == null) {
                friendUsername = Integer.toString(friendUserId);
            }
            if (!friendUsernamesToAdd.remove(friendUsername)) {
                friendsToDelete.add(friendUserId);
            }
        }

        // Delete
        if (!friendsToDelete.isEmpty()) {
            ctx
                .deleteFrom(TEAMPREFERENCES)
                .where(
                    TEAMPREFERENCES.EXERCISEID.eq(exerciseId),
                    TEAMPREFERENCES.USERID.eq(userId),
                    TEAMPREFERENCES.FRIEND_USERID.in(friendsToDelete)
                )
                .execute();
        }

        // Add
        if (!friendUsernamesToAdd.isEmpty()) {
            // Collect all numeric entries
            List<Integer> numericFriendUsernamesToAdd = new ArrayList<>(friendUsernamesToAdd.size());
            for (String username : friendUsernamesToAdd) {
                try {
                    numericFriendUsernamesToAdd.add(Integer.parseInt(username));
                } catch (NumberFormatException e) {
                    // ignored
                }
            }

            // Find matching students
            Students s = STUDENTS.as("s");
            Map<Integer, String> userIds = ctx
                .select(u.USERID, u.USERNAME)
                .from(s)
                .innerJoin(u).onKey(Keys.FK__STUDENTS__USERS)
                .where(
                    s.EXERCISEID.eq(exerciseId),
                    DSL.or(
                        u.USERNAME.in(friendUsernamesToAdd),
                        friendUsernamesToAdd.isEmpty() ? DSL.noCondition() : u.USERID.in(numericFriendUsernamesToAdd)
                    )
                )
                .fetchMap(Record2::value1, Record2::value2);

            // Keep only invalid usernames in friendUsernamesToAdd
            userIds.forEach((friendUserId, friendUsername) -> {
                friendUsernamesToAdd.remove(friendUserId.toString());
                if (friendUsername != null) {
                    friendUsernamesToAdd.remove(friendUsername);
                }
            });

            // cannot add yourself as friend
            userIds.remove(userId);

            // Add new friends
            if (!userIds.isEmpty()) {
                ctx
                    .batchInsert(
                        userIds.keySet().stream()
                            .map(friendUserId -> {
                                TeamPreferenceRecord record = ctx.newRecord(TEAMPREFERENCES);
                                record.setExerciseId(exerciseId);
                                record.setUserId(userId);
                                record.setFriendUserId(friendUserId);
                                return record;
                            })
                            .toArray(TeamPreferenceRecord[]::new)
                    )
                    .execute();
            }

            // Display errors for invalid names
            for (String invalidUsername : friendUsernamesToAdd) {
                addRedirectMessage(MessageType.ERROR, "Benutzername/-ID '" + invalidUsername + "' ist ungültig oder nicht für " + exerciseId + " angemeldet und konnte daher nicht übernommen werden.", redirectAttributes);
            }
        }

        addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        return "redirect:/exercise/{exerciseId}/groups/preferences";
    }

    private static class AssignmentData {
        @Nullable private BigDecimal points;
        @Nullable private String comment;
        private final List<UploadRecord> uploads = new ArrayList<>();
        @Nullable private TestResultRecord testResult;
    }

    private static class TeamData {
        private final Map<Integer, TeamResultData3.TeamMember> teamMembers = new HashMap<>();
        private final Map<String, AssignmentData> assignmentData = new HashMap<>();
        @Nullable String comment;
        boolean hideComments;
        boolean hidePoints;
    }

    @GetMapping("/sheet/{sheetId}/overview")
    public String getSheetOverviewPage(@PathVariable String exerciseId, @PathVariable String sheetId, Model model, ExerciseRoles exerciseRoles) {
        metricsService.registerAccess();
        model.addAttribute(
            "sheet",
            ctx
                .fetchOptional(SHEETS, SHEETS.EXERCISE.eq(exerciseId), SHEETS.ID.eq(sheetId))
                .orElseThrow(NotFoundException::new)
        );

        List<AssignmentWithTestInfo> assignments = ctx
            .fetchStream(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId))
            .sorted(Comparator.comparing(IAssignment::getAssignmentId, Comparators.IDENTIFIER))
            .map(assignment -> new AssignmentWithTestInfo(assignment, rteServices.isTestAvailable(exerciseId, sheetId, assignment.getAssignmentId())))
            .toList();
        model.addAttribute("assignments", assignments);

        // Student
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        if (groupAndTeam != null) {
            final TeamResultData3 teamResultData3;
            int userId = accessChecker.getUserId();
            String groupId;
            String teamId;
            StudentResultRecord studentResultRecord = ctx
                .fetchOne(
                    STUDENTRESULTS,
                    STUDENTRESULTS.EXERCISE.eq(exerciseId),
                    STUDENTRESULTS.SHEET.eq(sheetId),
                    STUDENTRESULTS.USERID.eq(userId)
                );
            if (studentResultRecord == null) {
                groupId = groupAndTeam.getGroupId();
                teamId = groupAndTeam.getTeamId();
            } else {
                groupId = studentResultRecord.getGroupId();
                teamId = studentResultRecord.getTeamId();
            }

            if (groupId != null && teamId != null) {
                Users u = USERS.as("u");
                Students s = STUDENTS.as("s");
                Studentresults sr = STUDENTRESULTS.as("sr");
                Map<Integer, TeamResultData3.TeamMember> teamMembers = ctx
                    .select(u.USERID, u.FIRSTNAME, u.LASTNAME, u.STUDENTID, sr.DELTAPOINTS, sr.DELTAPOINTS_REASON)
                    .from(sr)
                    .innerJoin(u).on(u.USERID.eq(sr.USERID))
                    .where(
                        sr.EXERCISE.eq(exerciseId),
                        sr.SHEET.eq(sheetId),
                        sr.GROUPID.eq(groupId),
                        sr.TEAMID.eq(teamId)
                    )
                    .unionAll(DSL
                        .select(u.USERID, u.FIRSTNAME, u.LASTNAME, u.STUDENTID, DSL.val((BigDecimal) null), DSL.val((String) null))
                        .from(s)
                        .innerJoin(u).onKey(Keys.FK__STUDENTS__USERS)
                        .where(
                            s.EXERCISEID.eq(exerciseId),
                            s.GROUPID.eq(groupId),
                            s.TEAMID.eq(teamId),
                            DSL.notExists(DSL
                                .selectOne()
                                .from(sr)
                                .where(
                                    sr.EXERCISE.eq(exerciseId),
                                    sr.SHEET.eq(sheetId),
                                    sr.USERID.eq(s.USERID)
                                )
                            )
                        )
                    )
                    .fetchMap(Record6::value1, Records.mapping(TeamResultData3.TeamMember::new));


                Teamresults tr = TEAMRESULTS.as("tr");
                TeamResultRecord teamResultRecord = ctx.fetchOne(tr, tr.EXERCISE.eq(exerciseId), tr.SHEET.eq(sheetId), tr.GROUPID.eq(groupId), tr.TEAMID.eq(teamId));

                Map<String, AssignmentData> assignmentDataMap = new HashMap<>();
                BiConsumer<String, Consumer<AssignmentData>> withAssignmentData =
                    (assignmentId, consumer) -> consumer.accept(
                        assignmentDataMap.computeIfAbsent(assignmentId, ignored -> new AssignmentData())
                    );

                // Assignment points
                if (teamResultRecord != null) {
                    TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
                    ctx
                        .select(tra.ASSIGNMENT, tra.POINTS, tra.COMMENT)
                        .from(tra)
                        .where(
                            tra.EXERCISE.eq(exerciseId),
                            tra.SHEET.eq(sheetId),
                            tra.GROUPID.eq(groupId),
                            tra.TEAMID.eq(teamId)
                        )
                        .forEach(r -> withAssignmentData.accept(
                            /* assignmentId */ r.value1(),
                            assignmentData -> {
                                assignmentData.points = r.value2();
                                assignmentData.comment = r.value3();
                            }
                        ));
                }

                // Uploads
                ctx
                    .selectFrom(UPLOADS)
                    .where(
                        UPLOADS.EXERCISE.eq(exerciseId),
                        UPLOADS.SHEET.eq(sheetId),
                        UPLOADS.GROUPID.eq(groupId),
                        UPLOADS.TEAMID.eq(teamId)
                    )
                    .forEach(upload -> withAssignmentData.accept(
                        upload.getAssignmentId(),
                        assignmentData -> assignmentData.uploads.add(upload)
                    ));

                // Testresults
                Testresult test = TESTRESULT.as("test");
                Testresult test2 = TESTRESULT.as("test2");
                ctx
                    .selectFrom(test)
                    .where(
                        test.EXERCISE.eq(exerciseId),
                        test.SHEET.eq(sheetId),
                        test.GROUPID.eq(groupId),
                        test.TEAMID.eq(teamId),
                        // Limit to most recent test
                        test.REQUESTNR.eq(DSL
                            .select(DSL.max(test2.REQUESTNR))
                            .from(test2)
                            .where(
                                test2.EXERCISE.eq(test.EXERCISE),
                                test2.SHEET.eq(test.SHEET),
                                test2.ASSIGNMENT.eq(test.ASSIGNMENT),
                                test2.GROUPID.eq(test.GROUPID),
                                test2.TEAMID.eq(test.TEAMID)
                            )
                        )
                    )
                    .forEach(testResult -> withAssignmentData.accept(
                        testResult.getAssignmentId(),
                        assignmentData -> assignmentData.testResult = testResult
                    ));

                Map<String, TeamResultData3.AssignmentResult> assignmentResults = assignmentDataMap.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                        AssignmentData assignmentData = e.getValue();
                        List<TeamResultData3.Upload> currentFiles = new ArrayList<>();
                        List<TeamResultData3.Upload> deletedFiles = new ArrayList<>();
                        for (UploadRecord upload : assignmentData.uploads) {
                            (upload.getDeleteDate() == null ? currentFiles : deletedFiles).add(new TeamResultData3.Upload(
                                upload.getFilename(),
                                upload.getUploadDate(),
                                upload.getDeleteDate(),
                                Optional.ofNullable(upload.getUploaderUserId()).map(teamMembers::get).orElse(null),
                                Optional.ofNullable(upload.getDeleterUserId()).map(teamMembers::get).orElse(null)
                            ));
                        }
                        currentFiles.sort(Comparator.comparing(TeamResultData3.Upload::getFilename));
                        deletedFiles.sort(Comparator
                            .comparing(TeamResultData3.Upload::getFilename)
                            .thenComparing(TeamResultData3.Upload::getUploadDate, Comparator.reverseOrder())
                        );
                        return new TeamResultData3.AssignmentResult(
                            assignmentData.points,
                            assignmentData.comment,
                            currentFiles,
                            deletedFiles,
                            assignmentData.uploads.stream()
                                .flatMap(upload -> Stream.of(upload.getUploadDate(), upload.getDeleteDate()))
                                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                            assignmentData.testResult
                        );
                    }
                ));

                if (teamResultRecord != null) {
                    teamResultData3 = new TeamResultData3(
                        groupId,
                        teamId,
                        sortedTeamMembers(teamMembers),
                        assignmentResults,
                        teamResultRecord.getComment(),
                        teamResultRecord.getHideComments(),
                        teamResultRecord.getHidePoints()
                    );
                } else {
                    teamResultData3 = new TeamResultData3(
                        groupId,
                        teamId,
                        sortedTeamMembers(teamMembers),
                        assignmentResults,
                        null,
                        false,
                        false
                    );
                }
            } else {
                teamResultData3 = null;
            }

            model.addAttribute("studentTeamResult", teamResultData3);
        }

        // Tutor / Assistant
        if (exerciseRoles.canAssess()) {
            Users u = USERS.as("u");
            Students s = STUDENTS.as("s");
            Studentresults sr = STUDENTRESULTS.as("sr");
            Teamresults tr = TEAMRESULTS.as("tr");
            TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
            Testresult test = TESTRESULT.as("test");
            Testresult test2 = TESTRESULT.as("test2");

            Map<NonNullGroupAndTeam, TeamData> teams = new HashMap<>();
            Consumer3<String, String, Consumer<TeamData>> withTeamData =
                (groupId, teamId, consumer) -> consumer.accept(
                    teams.computeIfAbsent(new NonNullGroupAndTeam(groupId, teamId), ignored -> new TeamData())
                );
            Consumer4<String, String, String, Consumer<AssignmentData>> withAssignmentData =
                (groupId, teamId, assignmentId, consumer) -> withTeamData.accept(
                    groupId,
                    teamId,
                    teamData -> consumer.accept(
                        teamData.assignmentData.computeIfAbsent(assignmentId, ignored -> new AssignmentData())
                    )
                );

            // Collect all teams and their members
            ctx
                .select(sr.GROUPID, sr.TEAMID, u.USERID, u.FIRSTNAME, u.LASTNAME, u.STUDENTID, sr.DELTAPOINTS, sr.DELTAPOINTS_REASON)
                .from(sr)
                .innerJoin(u).on(u.USERID.eq(sr.USERID))
                .where(
                    sr.EXERCISE.eq(exerciseId),
                    sr.SHEET.eq(sheetId),
                    sr.TEAMID.isNotNull(),
                    exerciseRoles.applyGroupIdRestriction(sr.GROUPID)
                )
                .unionAll(DSL
                    .select(s.GROUPID, s.TEAMID, u.USERID, u.FIRSTNAME, u.LASTNAME, u.STUDENTID, DSL.val((BigDecimal) null), DSL.val((String) null))
                    .from(s)
                    .innerJoin(u).onKey(Keys.FK__STUDENTS__USERS)
                    .where(
                        s.EXERCISEID.eq(exerciseId),
                        s.TEAMID.isNotNull(),
                        DSL.notExists(DSL
                            .selectOne()
                            .from(sr)
                            .where(
                                sr.EXERCISE.eq(exerciseId),
                                sr.SHEET.eq(sheetId),
                                sr.USERID.eq(s.USERID)
                            )
                        ),
                        exerciseRoles.applyGroupIdRestriction(s.GROUPID)
                    )
                )
                .forEach(r -> withTeamData.accept(
                    /* groupId */ r.value1(),
                    /* teamId */ r.value2(),
                    teamData -> teamData.teamMembers.put(
                        /* userId */ r.value3(),
                        new TeamResultData3.TeamMember(
                            /* userId */ r.value3(),
                            /* firstname */ r.value4(),
                            /* lastname */ r.value5(),
                            /* studentId */ r.value6(),
                            /* deltapoints */ r.value7(),
                            /* deltapointsReason */ r.value8()
                        )
                    )
                ));

            // Teamresults
            ctx
                .select(tr.GROUPID, tr.TEAMID, tr.COMMENT, tr.HIDECOMMENTS, tr.HIDEPOINTS)
                .from(tr)
                .where(
                    tr.EXERCISE.eq(exerciseId),
                    tr.SHEET.eq(sheetId),
                    exerciseRoles.applyGroupIdRestriction(tr.GROUPID)
                )
                .forEach(r -> withTeamData.accept(
                    /* groupId */ r.value1(),
                    /* teamId */ r.value2(),
                    teamData -> {
                        teamData.comment = r.value3();
                        teamData.hideComments = r.value4();
                        teamData.hidePoints = r.value5();
                    }
                ));

            // Assignment points
            ctx
                .select(tra.GROUPID, tra.TEAMID, tra.ASSIGNMENT, tra.POINTS, tra.COMMENT)
                .from(tra)
                .where(
                    tra.EXERCISE.eq(exerciseId),
                    tra.SHEET.eq(sheetId),
                    exerciseRoles.applyGroupIdRestriction(tra.GROUPID)
                )
                .forEach(r -> withAssignmentData.accept(
                    /* groupId */ r.value1(),
                    /* teamId */ r.value2(),
                    /* assignmentId */ r.value3(),
                    assignmentData -> {
                        assignmentData.points = r.value4();
                        assignmentData.comment = r.value5();
                    }
                ));

            // Uploads
            ctx
                .selectFrom(UPLOADS)
                .where(
                    UPLOADS.EXERCISE.eq(exerciseId),
                    UPLOADS.SHEET.eq(sheetId),
                    exerciseRoles.applyGroupIdRestriction(UPLOADS.GROUPID)
                )
                .forEach(upload -> withAssignmentData.accept(
                    upload.getGroupId(),
                    upload.getTeamId(),
                    upload.getAssignmentId(),
                    assignmentData -> assignmentData.uploads.add(upload)
                ));

            // Testresults
            ctx
                .selectFrom(test)
                .where(
                    test.EXERCISE.eq(exerciseId),
                    test.SHEET.eq(sheetId),
                    // Limit to most recent test
                    test.REQUESTNR.eq(DSL
                        .select(DSL.max(test2.REQUESTNR))
                        .from(test2)
                        .where(
                            test2.EXERCISE.eq(test.EXERCISE),
                            test2.SHEET.eq(test.SHEET),
                            test2.ASSIGNMENT.eq(test.ASSIGNMENT),
                            test2.GROUPID.eq(test.GROUPID),
                            test2.TEAMID.eq(test.TEAMID)
                        )
                    ),
                    exerciseRoles.applyGroupIdRestriction(test.GROUPID)
                )
                .forEach(testResult -> withAssignmentData.accept(
                    testResult.getGroupId(),
                    testResult.getTeamId(),
                    testResult.getAssignmentId(),
                    assignmentData -> assignmentData.testResult = testResult
                ));

            model.addAttribute(
                "teamResults",
                teams.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(GroupAndTeam::getGroupId, Comparators.IDENTIFIER)
                        .thenComparing(GroupAndTeam::getTeamId, Comparators.IDENTIFIER))
                    )
                    .map(entry -> {
                        NonNullGroupAndTeam nonNullGroupAndTeam = entry.getKey();
                        TeamData teamData = entry.getValue();
                        return new TeamResultData3(
                            nonNullGroupAndTeam.getGroupId(),
                            nonNullGroupAndTeam.getTeamId(),
                            sortedTeamMembers(teamData.teamMembers),
                            teamData.assignmentData.entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> {
                                    AssignmentData assignmentData = e.getValue();
                                    List<TeamResultData3.Upload> currentFiles = new ArrayList<>();
                                    List<TeamResultData3.Upload> deletedFiles = new ArrayList<>();
                                    for (UploadRecord upload : assignmentData.uploads) {
                                        (upload.getDeleteDate() == null ? currentFiles : deletedFiles).add(new TeamResultData3.Upload(
                                            upload.getFilename(),
                                            upload.getUploadDate(),
                                            upload.getDeleteDate(),
                                            Optional.ofNullable(upload.getUploaderUserId()).map(teamData.teamMembers::get).orElse(null),
                                            Optional.ofNullable(upload.getDeleterUserId()).map(teamData.teamMembers::get).orElse(null)
                                        ));
                                    }
                                    currentFiles.sort(Comparator.comparing(TeamResultData3.Upload::getFilename));
                                    deletedFiles.sort(Comparator
                                        .comparing(TeamResultData3.Upload::getFilename)
                                        .thenComparing(TeamResultData3.Upload::getUploadDate, Comparator.reverseOrder())
                                    );
                                    return new TeamResultData3.AssignmentResult(
                                        assignmentData.points,
                                        assignmentData.comment,
                                        currentFiles,
                                        deletedFiles,
                                        assignmentData.uploads.stream()
                                            .flatMap(upload -> Stream.of(upload.getUploadDate(), upload.getDeleteDate()))
                                            .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                                        assignmentData.testResult
                                    );
                                }
                            )),
                            teamData.comment,
                            teamData.hideComments,
                            teamData.hidePoints
                        );
                    })
                    .toList()
            );
        }

        return "exercise/sheet";
    }

    @GetMapping("/sheet/{sheetId}/fragment/{assignmentId}/{groupId}/{teamId}")
    public String getSheetFragment(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();

        if (!exerciseRoles.canAssess(groupId)) {
            // Check if student can access
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(accessChecker.getUserId())
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        // Testresult
        Testresult test = TESTRESULT.as("test");
        Testresult test2 = TESTRESULT.as("test2");
        TestResultRecord testResult = ctx
            .selectFrom(test)
            .where(
                test.EXERCISE.eq(exerciseId),
                test.SHEET.eq(sheetId),
                test.ASSIGNMENT.eq(assignmentId),
                test.GROUPID.eq(groupId),
                test.TEAMID.eq(teamId),
                // Limit to most recent test
                test.REQUESTNR.eq(DSL
                    .select(DSL.max(test2.REQUESTNR))
                    .from(test2)
                    .where(
                        test2.EXERCISE.eq(test.EXERCISE),
                        test2.SHEET.eq(test.SHEET),
                        test2.ASSIGNMENT.eq(test.ASSIGNMENT),
                        test2.GROUPID.eq(test.GROUPID),
                        test2.TEAMID.eq(test.TEAMID)
                    )
                )
            )
            .fetchOne();
        model.addAttribute("testResult", testResult);

        // Snapshot
        LocalDateTime snapshot = ctx
            .select(DSL.max(DSL.greatest(UPLOADS.UPLOAD_DATE, UPLOADS.DELETE_DATE)))
            .from(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId)
            )
            .fetchOne(Record1::value1);
        model.addAttribute("snapshot", snapshot);

        return "exercise/sheet-assignment-fragment";
    }

    private static List<TeamResultData3.TeamMember> sortedTeamMembers(Map<Integer, TeamResultData3.TeamMember> teamMembers) {
        return teamMembers.values().stream()
            .sorted(Comparator
                .comparing(TeamResultData3.TeamMember::getLastname)
                .thenComparing(TeamResultData3.TeamMember::getFirstname)
                .thenComparing(TeamResultData3.TeamMember::getUserId))
            .toList();
    }

    @Value
    public static class UploadModel {
        UploadRecord upload;
        PreviewFileType previewType;
        @Nullable String langClass;
        @Nullable String fileContent;

        public String getInternalFilename() {
            return INTERNAL_DTF.format(upload.getUploadDate()) + "-" + upload.getFilename();
        }
    }

    @GetMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/view/{internalFilename:\\d{14}-.+}")
    public String getViewFilePage(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable String internalFilename,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        boolean canAssess = exerciseRoles.canAssess(groupId);
        if (!canAssess) {
            // Check if student can access that file
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(userId)
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        LocalDateTime uploadDate = LocalDateTime.parse(internalFilename.substring(0, 14), INTERNAL_DTF);
        String filename = internalFilename.substring(15);

        UploadRecord upload = ctx
            .selectFrom(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.UPLOAD_DATE.eq(uploadDate),
                UPLOADS.FILENAME.eq(filename)
            )
            .orderBy(UPLOADS.ID.desc())
            .limit(1)
            .fetchOptional()
            .orElseThrow(NotFoundException::new);
        int dotIndex = filename.lastIndexOf(".");
        String extension = dotIndex < 0 ? "" : filename.substring(dotIndex + 1);

        PreviewFileType previewFileType = PreviewFileType.byExtension(extension);
        String langClass = null;
        String fileContent = null;
        if (previewFileType == PreviewFileType.Text) {
            int fileId = upload.getUploadId();
            Map<Integer, AnnotationRecord> annotationsByLine;
            if (canAssess
                || !ctx
                .select(TEAMRESULTS.HIDECOMMENTS)
                .from(TEAMRESULTS)
                .where(
                    TEAMRESULTS.EXERCISE.eq(exerciseId),
                    TEAMRESULTS.SHEET.eq(sheetId),
                    TEAMRESULTS.GROUPID.eq(groupId),
                    TEAMRESULTS.TEAMID.eq(teamId)
                )
                .fetchOptional(Record1::value1)
                .orElse(false)
            ) {
                annotationsByLine = ctx
                    .selectFrom(ANNOTATIONS)
                    .where(ANNOTATIONS.FILEID.eq(fileId))
                    .fetchMap(AnnotationRecord::getLine);

                // Mark read
                ctx
                    .deleteFrom(UNREAD)
                    .where(
                        UNREAD.FILEID.eq(fileId),
                        UNREAD.USERID.eq(userId)
                    )
                    .execute();
            } else {
                annotationsByLine = Collections.emptyMap();
            }
            Map<Integer, List<WarningRecord>> warningsByLine = new HashMap<>();
            ctx
                .selectFrom(WARNINGS)
                .where(WARNINGS.FILEID.eq(fileId))
                .forEach(warning -> warningsByLine
                    .computeIfAbsent(warning.getLine(), ignored -> new ArrayList<>())
                    .add(warning)
                );

            Path file = uploadManager.getUploadPath(upload);
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPLACE);

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder))) {
                String line;
                int linenum = 1;
                while ((line = reader.readLine()) != null) {
                    builder.append(HtmlUtils.htmlEscape(line.replaceAll("\t", "    ")));
                    AnnotationRecord annotation = annotationsByLine.get(linenum);
                    if (annotation != null) {
                        builder.append("<div class=\"annotationbox commentbox popover right nocode\"><div class=\"arrow\"></div><div class=\"comment popover-content\">");
                        String html = Markdown.toHtml(annotation.getAnnotationObj());
                        html = html.replace("\n", " ").replace("\r", " ");
                        builder.append(html);
                        builder.append("</div></div>");
                    }
                    List<WarningRecord> lineWarnings = warningsByLine.get(linenum);
                    if (lineWarnings != null) {
                        for (WarningRecord warning : lineWarnings) {
                            String message = warning.getMessage();
                            String infoUrl = warning.getInfoUrl();
                            String markdown = infoUrl == null
                                ? message
                                : Objects.toString(message, "") + "\n [Weitere Infos](" + infoUrl + ")";
                            if (markdown != null) {
                                builder.append("<div class=\"warningbox commentbox popover right nocode\"><div class=\"comment popover-content\">");
                                String html = Markdown.toHtml(markdown);
                                html = html.replace("\n", " ").replace("\r", " ");
                                builder.append(html);
                                builder.append("</div></div>");
                            }
                        }
                    }
                    builder.append(System.lineSeparator());
                    linenum++;
                }
            } catch (IOException e) {
                log.error("Error reading upload {}", upload, e);
                builder.append("Error loading file ").append(filename);
            }

            langClass = PreviewFileType.LANG_CLASS_MAPPING.getOrDefault(extension, "");
            fileContent = builder.toString();
        }

        model.addAttribute("upload", new UploadModel(
            upload, previewFileType, langClass, fileContent
        ));
        model.addAttribute("canAssess", canAssess);

        return "exercise/file-page";
    }

    @GetMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/view")
    public String getViewFilesPage(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        return getViewFilesPage(exerciseId, sheetId, assignmentId, groupId, teamId, null, model, exerciseRoles);
    }

    @GetMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/view/{snapshot:\\d{14}}")
    public String getViewFilesPage(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @Nullable @PathVariable String snapshot,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        boolean canAssess = exerciseRoles.canAssess(groupId);
        if (!canAssess) {
            // Check if student can access that file
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(userId)
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        LocalDateTime parsedSnapshot = snapshot == null ? null : LocalDateTime.parse(snapshot, INTERNAL_DTF);
        List<UploadRecord> uploads = ctx
            .fetch(
                UPLOADS,
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                parsedSnapshot == null ? UPLOADS.DELETE_DATE.isNull() : DSL.and(
                    UPLOADS.UPLOAD_DATE.le(parsedSnapshot),
                    UPLOADS.DELETE_DATE.isNull().or(UPLOADS.DELETE_DATE.gt(parsedSnapshot))
                )
            );
        List<UploadModel> uploadModels = new ArrayList<>(uploads.size());
        if (!uploads.isEmpty()) {
            List<Integer> markAsRead = new ArrayList<>(uploads.size());

            for (UploadRecord upload : uploads) {
                String filename = upload.getFilename();
                int dotIndex = filename.lastIndexOf(".");
                String extension = dotIndex < 0 ? "" : filename.substring(dotIndex + 1);

                PreviewFileType previewFileType = PreviewFileType.byExtension(extension);
                String langClass = null;
                String fileContent = null;
                if (previewFileType == PreviewFileType.Text) {
                    int fileId = upload.getUploadId();
                    Map<Integer, AnnotationRecord> annotationsByLine;
                    if (canAssess
                        || !ctx
                        .select(TEAMRESULTS.HIDECOMMENTS)
                        .from(TEAMRESULTS)
                        .where(
                            TEAMRESULTS.EXERCISE.eq(exerciseId),
                            TEAMRESULTS.SHEET.eq(sheetId),
                            TEAMRESULTS.GROUPID.eq(groupId),
                            TEAMRESULTS.TEAMID.eq(teamId)
                        )
                        .fetchOptional(Record1::value1)
                        .orElse(false)
                    ) {
                        annotationsByLine = ctx
                            .selectFrom(ANNOTATIONS)
                            .where(ANNOTATIONS.FILEID.eq(fileId))
                            .fetchMap(AnnotationRecord::getLine);
                        markAsRead.add(fileId);
                    } else {
                        annotationsByLine = Collections.emptyMap();
                    }
                    Map<Integer, List<WarningRecord>> warningsByLine = new HashMap<>();
                    ctx
                        .selectFrom(WARNINGS)
                        .where(WARNINGS.FILEID.eq(fileId))
                        .forEach(warning -> warningsByLine
                            .computeIfAbsent(warning.getLine(), ignored -> new ArrayList<>())
                            .add(warning)
                        );

                    Path file = uploadManager.getUploadPath(upload);
                    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                    decoder.onMalformedInput(CodingErrorAction.REPLACE);

                    StringBuilder builder = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder))) {
                        String line;
                        int linenum = 1;
                        while ((line = reader.readLine()) != null) {
                            builder.append(HtmlUtils.htmlEscape(line.replaceAll("\t", "    ")));
                            AnnotationRecord annotation = annotationsByLine.get(linenum);
                            if (annotation != null) {
                                builder.append("<div class=\"annotationbox commentbox popover right nocode\"><div class=\"arrow\"></div><div class=\"comment popover-content\">");
                                String html = Markdown.toHtml(annotation.getAnnotationObj());
                                html = html.replace("\n", " ").replace("\r", " ");
                                builder.append(html);
                                builder.append("</div></div>");
                            }
                            List<WarningRecord> lineWarnings = warningsByLine.get(linenum);
                            if (lineWarnings != null) {
                                for (WarningRecord warning : lineWarnings) {
                                    String message = warning.getMessage();
                                    String infoUrl = warning.getInfoUrl();
                                    String markdown = infoUrl == null
                                        ? message
                                        : Objects.toString(message, "") + "\n [Weitere Infos](" + infoUrl + ")";
                                    if (markdown != null) {
                                        builder.append("<div class=\"warningbox commentbox popover right nocode\"><div class=\"comment popover-content\">");
                                        String html = Markdown.toHtml(markdown);
                                        html = html.replace("\n", " ").replace("\r", " ");
                                        builder.append(html);
                                        builder.append("</div></div>");
                                    }
                                }
                            }
                            builder.append(System.lineSeparator());
                            linenum++;
                        }
                    } catch (IOException e) {
                        log.error("Error reading upload {}", upload, e);
                        builder.append("Error loading file ").append(filename);
                    }

                    langClass = PreviewFileType.LANG_CLASS_MAPPING.getOrDefault(extension, "");
                    fileContent = builder.toString();
                }
                uploadModels.add(new UploadModel(upload, previewFileType, langClass, fileContent));
            }

            if (!markAsRead.isEmpty()) {
                ctx
                    .deleteFrom(UNREAD)
                    .where(
                        UNREAD.FILEID.in(markAsRead),
                        UNREAD.USERID.eq(userId)
                    )
                    .execute();
            }
        }

        model.addAttribute("parsedSnapshot", parsedSnapshot);
        model.addAttribute("uploads", uploadModels);
        model.addAttribute("canAssess", canAssess);

        return "exercise/files-page";
    }

    @PostMapping("/sheet/{sheetId}/upload")
    @ResponseBody
    @Transactional
    public ResponseEntity<String> uploadFile(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @RequestParam String groupId,
        @RequestParam String teamId,
        @RequestParam String assignmentId,
        @RequestParam MultipartFile file,
        ExerciseRoles exerciseRoles
    ) throws IOException {
        metricsService.registerAccess();
        String filename = file.getOriginalFilename();
        if (StringUtils.isEmpty(filename)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Keine Datei angegeben");
        }

        // Check if student can upload for that team
        int userId = accessChecker.getUserId();
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        StudentResultRecord studentResultRecord;
        if (groupAndTeam == null) {
            throw new AccessDeniedException("Not a student in that exercise");
        } else {
            studentResultRecord = ctx.fetchOne(
                STUDENTRESULTS,
                STUDENTRESULTS.EXERCISE.eq(exerciseId),
                STUDENTRESULTS.SHEET.eq(sheetId),
                STUDENTRESULTS.USERID.eq(userId)
            );
            if (studentResultRecord != null) {
                groupAndTeam = new GroupAndTeam(studentResultRecord.getGroupId(), studentResultRecord.getTeamId());
            }
        }
        if (!groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
            throw new AccessDeniedException("Cannot access that team");
        }

        if (studentResultRecord == null) {
            studentResultRecord = ctx.newRecord(STUDENTRESULTS);
            studentResultRecord.setExerciseId(exerciseId);
            studentResultRecord.setSheetId(sheetId);
            studentResultRecord.setUserId(userId);
            studentResultRecord.setGroupId(groupId);
            studentResultRecord.setTeamId(teamId);
            studentResultRecord.insert();
        }

        LocalDateTime uploadDate = LocalDateTime.now(exclaimProperties.getTimezone()).truncatedTo(ChronoUnit.SECONDS);

        ctx
            .update(UPLOADS)
            .set(UPLOADS.DELETE_DATE, uploadDate)
            .set(UPLOADS.DELETER_USERID, userId)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.FILENAME.eq(filename),
                UPLOADS.DELETE_DATE.isNull()
            )
            .execute();

        UploadRecord record = ctx.newRecord(UPLOADS);
        record.setExerciseId(exerciseId);
        record.setSheetId(sheetId);
        record.setAssignmentId(assignmentId);
        record.setGroupId(groupId);
        record.setTeamId(teamId);
        record.setFilename(filename);
        record.setUploaderUserId(userId);
        record.setUploadDate(uploadDate);
        record.insert();

        Path destination = uploadManager.getUploadPath(record);
        File destinationDir = Objects.requireNonNull(destination.getParent()).toFile();
        destinationDir.mkdirs();
        file.transferTo(destination);

        return ResponseEntity.ok("Datei hochgeladen");
    }

    @PostMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/delete/{internalFilename:\\d{14}-.+}")
    public String deleteUpload(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable String internalFilename,
        RedirectAttributes redirectAttributes,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();

        // Check if student can upload for that team
        int userId = accessChecker.getUserId();
        GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
        StudentResultRecord studentResultRecord;
        if (groupAndTeam == null) {
            throw new AccessDeniedException("Not a student in that exercise");
        } else {
            studentResultRecord = ctx.fetchOne(
                STUDENTRESULTS,
                STUDENTRESULTS.EXERCISE.eq(exerciseId),
                STUDENTRESULTS.SHEET.eq(sheetId),
                STUDENTRESULTS.USERID.eq(userId)
            );
            if (studentResultRecord != null) {
                groupAndTeam = new GroupAndTeam(studentResultRecord.getGroupId(), studentResultRecord.getTeamId());
            }
        }
        if (!groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
            throw new AccessDeniedException("Cannot access that team");
        }

        LocalDateTime uploadDate = LocalDateTime.parse(internalFilename.substring(0, 14), INTERNAL_DTF);
        String filename = internalFilename.substring(15);

        UploadRecord upload = ctx
            .selectFrom(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.UPLOAD_DATE.eq(uploadDate),
                UPLOADS.FILENAME.eq(filename)
            )
            .orderBy(UPLOADS.ID.desc())
            .limit(1)
            .fetchOptional()
            .orElseThrow(NotFoundException::new);

        if (upload.getDeleteDate() == null) {
            upload.setDeleteDate(LocalDateTime.now(exclaimProperties.getTimezone()));
            upload.setDeleterUserId(userId);
            upload.update();
            addRedirectMessage(MessageType.SUCCESS, "Die Datei " + upload.getFilename() + " wurde in den Papierkorb verschoben.", redirectAttributes);
        }

        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/overview";
    }

    @GetMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/test/{requestNr}")
    public String getViewTestResultPage(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable int requestNr,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        boolean canAssess = exerciseRoles.canAssess(groupId);
        if (!canAssess) {
            // Check if student can access that file
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(userId)
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        TestResultRecord testResult = ctx
            .fetchOptional(TESTRESULT,
                TESTRESULT.EXERCISE.eq(exerciseId),
                TESTRESULT.SHEET.eq(sheetId),
                TESTRESULT.ASSIGNMENT.eq(assignmentId),
                TESTRESULT.GROUPID.eq(groupId),
                TESTRESULT.TEAMID.eq(teamId),
                TESTRESULT.REQUESTNR.eq(requestNr)
            )
            .orElseThrow(NotFoundException::new);
        String resultString = testResult.getResult();
        if (StringUtils.isNotEmpty(resultString)) {
            model.addAttribute("testResultDetails", TestResultDetails.fromJson(resultString));
        }
        model.addAttribute("testResult", testResult);
        return "exercise/testresult";
    }

    @PostMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/test")
    @Transactional
    public String requestTest(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @RequestParam(required = false, defaultValue = "") String snapshot,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        boolean canAssess = exerciseRoles.canAssess(groupId);
        if (!canAssess) {
            // Check if student can access that file
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(userId)
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        LocalDateTime timeRequest = LocalDateTime.now(exclaimProperties.getTimezone());

        // TODO: parsing snapshot time is broken
        snapshot = "";

        LocalDateTime snapshotTime;
        if (StringUtils.isEmpty(snapshot)) {
            snapshotTime = ctx
                .select(DSL.greatest(DSL.max(UPLOADS.UPLOAD_DATE), DSL.max(UPLOADS.DELETE_DATE)).as("snapshot"))
                .from(UPLOADS)
                .where(
                    UPLOADS.EXERCISE.eq(exerciseId),
                    UPLOADS.SHEET.eq(sheetId),
                    UPLOADS.ASSIGNMENT.eq(assignmentId),
                    UPLOADS.GROUPID.eq(groupId),
                    UPLOADS.TEAMID.eq(teamId)
                )
                .fetchOptional(Record1::value1)
                .orElse(timeRequest.truncatedTo(ChronoUnit.SECONDS));
        } else {
            snapshotTime = LocalDateTime.parse(snapshot, INTERNAL_DTF);
        }

        // Find next request number
        int requestNr = ctx
            .select(DSL.ifnull(DSL.max(TESTRESULT.REQUESTNR).plus(1), 1).as("next"))
            .from(TESTRESULT)
            .where(
                TESTRESULT.EXERCISE.eq(exerciseId),
                TESTRESULT.SHEET.eq(sheetId),
                TESTRESULT.ASSIGNMENT.eq(assignmentId),
                TESTRESULT.GROUPID.eq(groupId),
                TESTRESULT.TEAMID.eq(teamId)
            )
            .fetchSingle(Record1::value1);

        TestResultRecord record = ctx.newRecord(TESTRESULT);
        record.setExerciseId(exerciseId);
        record.setSheetId(sheetId);
        record.setAssignmentId(assignmentId);
        record.setGroupId(groupId);
        record.setTeamId(teamId);
        record.setRequestNr(requestNr);
        record.setTimeRequest(timeRequest);
        record.setSnapshot(snapshotTime);
        record.insert();

        runTest.submit(exerciseId, sheetId, assignmentId, groupId, teamId, requestNr);
        backgroundJobExecutor.pollNow();
        broker.convertAndSend(
            RteServices.testResultsChannel(exerciseId, sheetId, groupId, teamId),
            new RteServices.TestResultMsg(exerciseId, sheetId, assignmentId, groupId, teamId, requestNr, "started")
        );

        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/overview";
    }
}
