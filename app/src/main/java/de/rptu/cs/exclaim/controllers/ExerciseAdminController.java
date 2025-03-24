package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import de.rptu.cs.exclaim.data.GroupAndTeam;
import de.rptu.cs.exclaim.data.GroupWithCurrentSizeAndTutors;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.AssignmentRecord;
import de.rptu.cs.exclaim.data.records.ExerciseRecord;
import de.rptu.cs.exclaim.data.records.GroupRecord;
import de.rptu.cs.exclaim.data.records.SheetRecord;
import de.rptu.cs.exclaim.data.records.StudentRecord;
import de.rptu.cs.exclaim.data.records.TutorRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.optimus.Optimus;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.schema.enums.GroupPreferenceOption;
import de.rptu.cs.exclaim.schema.enums.Weekday;
import de.rptu.cs.exclaim.schema.tables.Groups;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.schema.tables.Tutors;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.utils.Comparators;
import de.rptu.cs.exclaim.utils.JsonUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record5;
import org.jooq.Records;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.ASSIGNMENT_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.ASSIGNMENT_LABEL_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.GROUP_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.GROUP_LOCATION_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.GROUP_TIME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.ID_REGEX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.SHEET_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.SHEET_LABEL_LENGTH_MAX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addMessage;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Assignments.ASSIGNMENTS;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Grouppreferences.GROUPPREFERENCES;
import static de.rptu.cs.exclaim.schema.tables.Groups.GROUPS;
import static de.rptu.cs.exclaim.schema.tables.Sheets.SHEETS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Teampreferences.TEAMPREFERENCES;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Controller
@RequestMapping("/exercise/{exerciseId}/admin")
@PreAuthorize("@accessChecker.isAssistantFor(#exerciseId)")
@RequiredArgsConstructor
@Slf4j
public class ExerciseAdminController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final Optimus optimus;
    private final DSLContext ctx;

    @Value
    public static class CreateGroupForm {
        @NotBlank @Size(max = GROUP_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String groupId;
        @Nullable Weekday day;
        @Nullable @Size(max = GROUP_TIME_LENGTH_MAX) String time;
        @Nullable @Size(max = GROUP_LOCATION_LENGTH_MAX) String location;
        @Nullable String maxSize;
    }

    @Value
    public static class EditGroupForm {
        @Nullable Weekday day;
        @Nullable @Size(max = GROUP_TIME_LENGTH_MAX) String time;
        @Nullable @Size(max = GROUP_LOCATION_LENGTH_MAX) String location;
        @Nullable String maxSize;
    }

    @Value
    public static class CreateSheetForm {
        @NotBlank @Size(max = SHEET_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String sheetId;
        @NotBlank @Size(max = SHEET_LABEL_LENGTH_MAX) String label;
    }

    @Value
    public static class EditSheetForm {
        @NotBlank @Size(max = SHEET_LABEL_LENGTH_MAX) String label;
    }

    @Value
    public static class CreateAssignmentForm {
        public CreateAssignmentForm(String assignmentId, String label, @Nullable String maxPoints, @Nullable Boolean showStatistics) {
            this.assignmentId = assignmentId;
            this.label = label;
            this.maxPoints = maxPoints;
            this.showStatistics = showStatistics != null && showStatistics;
        }

        @NotBlank @Size(max = ASSIGNMENT_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String assignmentId;
        @NotBlank @Size(max = ASSIGNMENT_LABEL_LENGTH_MAX) String label;
        @Nullable String maxPoints;
        boolean showStatistics;
    }

    @Value
    public static class EditAssignmentForm {
        public EditAssignmentForm(String label, @Nullable String maxPoints, @Nullable Boolean showStatistics) {
            this.label = label;
            this.maxPoints = maxPoints;
            this.showStatistics = showStatistics != null && showStatistics;
        }

        @NotBlank @Size(max = ASSIGNMENT_LABEL_LENGTH_MAX) String label;
        @Nullable String maxPoints;
        boolean showStatistics;
    }

    @GetMapping("/groups")
    public String getGroupsPage(@PathVariable String exerciseId, Model model) {
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
        model.addAttribute("groups", groups);
        return "exercise/groups-admin";
    }

    @PostMapping("")
    public String editExerciseSettings(@PathVariable String exerciseId, @RequestParam boolean registrationOpen, @RequestParam GroupJoin groupJoin, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExerciseRecord exerciseRecord = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        exerciseRecord.setRegistrationOpenIfChanged(registrationOpen);
        exerciseRecord.setGroupJoinIfChanged(groupJoin);
        if (exerciseRecord.changed()) {
            exerciseRecord.update();
            log.info("Exercise settings for {} have been changed by assistant {}", exerciseRecord, accessChecker.getUser());
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/admin/groups";
    }

    @GetMapping("/groups/create")
    public String getCreateGroupPage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateGroupForm("", null, "", "", ""));
        return "exercise/create-group";
    }

    @PostMapping("/groups/create")
    public String createGroup(@PathVariable String exerciseId, @Valid CreateGroupForm createGroupForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        Integer maxSize = null;
        try {
            maxSize = parseMaxSize(createGroupForm.maxSize);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxSize", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            GroupRecord groupRecord = ctx.newRecord(GROUPS);
            groupRecord.setExerciseId(exerciseId);
            groupRecord.setGroupId(createGroupForm.groupId);
            groupRecord.setDay(createGroupForm.day);
            groupRecord.setTime(StringUtils.defaultString(createGroupForm.time));
            groupRecord.setLocation(StringUtils.defaultString(createGroupForm.location));
            groupRecord.setMaxSize(maxSize);
            try {
                groupRecord.insert();
                log.info("Group {} has been created by assistant {}", groupRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/admin/groups";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("groupId", "Unique");
            }
        }
        return "exercise/create-group";
    }

    @GetMapping("/groups/{groupId}/edit")
    public String getEditGroupPage(@PathVariable String exerciseId, @PathVariable String groupId, Model model) {
        metricsService.registerAccess();
        GroupRecord groupRecord = ctx
            .fetchOptional(GROUPS, GROUPS.EXERCISEID.eq(exerciseId), GROUPS.GROUPID.eq(groupId))
            .orElseThrow(NotFoundException::new);
        model.addAttribute(new EditGroupForm(
            groupRecord.getDay(),
            groupRecord.getTime(),
            groupRecord.getLocation(),
            Optional.ofNullable(groupRecord.getMaxSize()).map(Object::toString).orElse("")
        ));
        return "exercise/edit-group";
    }

    @PostMapping("/groups/{groupId}/edit")
    public String editGroup(@PathVariable String exerciseId, @PathVariable String groupId, @Valid EditGroupForm editGroupForm, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        Integer maxSize = null;
        try {
            maxSize = parseMaxSize(editGroupForm.maxSize);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxSize", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            GroupRecord groupRecord = ctx
                .fetchOptional(GROUPS, GROUPS.EXERCISEID.eq(exerciseId), GROUPS.GROUPID.eq(groupId))
                .orElseThrow(NotFoundException::new);
            groupRecord.setDayIfChanged(editGroupForm.day);
            groupRecord.setTimeIfChanged(StringUtils.defaultString(editGroupForm.time));
            groupRecord.setLocationIfChanged(StringUtils.defaultString(editGroupForm.location));
            groupRecord.setMaxSizeIfChanged(maxSize);
            if (groupRecord.changed()) {
                groupRecord.update();
                log.info("Group {} has been changed by assistant {}", groupRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/admin/groups";
        }
        return "exercise/edit-group";
    }

    @PostMapping("/groups/{groupId}/delete")
    public String deleteGroup(@PathVariable String exerciseId, @PathVariable String groupId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        // TODO: Translations
        try {
            if (ctx
                .deleteFrom(GROUPS)
                .where(GROUPS.EXERCISEID.eq(exerciseId), GROUPS.GROUPID.eq(groupId))
                .execute() == 1
            ) {
                addRedirectMessage(MessageType.SUCCESS, "Gruppe " + groupId + " wurde gelöscht.", redirectAttributes);
            } else {
                addRedirectMessage(MessageType.ERROR, "Gruppe " + groupId + " konnte nicht gelöscht werden.", redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, "Gruppe " + groupId + " konnte aufgrund von vorhandenen Daten nicht gelöscht werden.\n" + e, redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/admin/groups";
    }

    @GetMapping("/groups/{groupId}/tutors")
    public String getTutorsPage(@PathVariable String exerciseId, @PathVariable String groupId, Model model) {
        metricsService.registerAccess();
        Tutors t = TUTORS.as("t");
        Users u = t.user().as("u");
        List<IUser> tutors = ctx
            .select(u)
            .from(t)
            .where(t.EXERCISEID.eq(exerciseId), t.GROUPID.eq(groupId))
            .fetch(Record1::value1);
        tutors.sort(Comparators.USER_BY_NAME);
        model.addAttribute("tutors", tutors);
        return "exercise/tutors";
    }

    @PostMapping("/groups/{groupId}/tutors")
    @Transactional
    public String addTutor(@PathVariable String exerciseId, @PathVariable String groupId, @RequestParam String username, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        GroupRecord groupRecord = ctx
            .fetchOptional(GROUPS, GROUPS.EXERCISEID.eq(exerciseId), GROUPS.GROUPID.eq(groupId))
            .orElseThrow(NotFoundException::new);
        Integer tutorUserId = ctx
            .select(USERS.USERID)
            .from(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOne(Record1::value1);
        if (tutorUserId != null) {
            TutorRecord tutor = ctx.newRecord(TUTORS);
            tutor.setExerciseId(groupRecord.getExerciseId());
            tutor.setGroupId(groupRecord.getGroupId());
            tutor.setUserId(tutorUserId);
            try {
                tutor.insert();
                log.info("Tutor {} has been added by assistant {}", tutor, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            } catch (DuplicateKeyException e) {
                // ignore duplicate tutor role
            }
        } else {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("common.username-not-found"), redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/admin/groups/{groupId}/tutors";
    }

    @PostMapping("/groups/{groupId}/tutors/{userId}/delete")
    @Transactional
    public String deleteTutor(@PathVariable String exerciseId, @PathVariable String groupId, @PathVariable int userId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (ctx
            .deleteFrom(TUTORS)
            .where(TUTORS.USERID.eq(userId), TUTORS.EXERCISEID.eq(exerciseId), TUTORS.GROUPID.eq(groupId))
            .execute() == 1
        ) {
            log.info("Tutor user id {} for exercise {} group {} has been deleted by assistant {}", userId, exerciseId, groupId, accessChecker.getUser());
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/admin/groups/{groupId}/tutors";
    }

    @GetMapping("/sheets/create")
    public String getCreateSheetPage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateSheetForm("", ""));
        return "exercise/create-sheet";
    }

    @PostMapping("/sheets/create")
    public String createSheet(@PathVariable String exerciseId, @Valid CreateSheetForm createSheetForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!bindingResult.hasErrors()) {
            SheetRecord sheetRecord = ctx.newRecord(SHEETS);
            sheetRecord.setExerciseId(exerciseId);
            sheetRecord.setSheetId(createSheetForm.sheetId);
            sheetRecord.setLabel(createSheetForm.label);
            try {
                sheetRecord.insert();
                log.info("Sheet {} has been created by assistant {}", sheetRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/admin/sheets/" + sheetRecord.getSheetId() + "/edit";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("sheetId", "Unique");
            }
        }
        return "exercise/create-sheet";
    }

    @GetMapping("/sheets/{sheetId}/edit")
    public String getEditSheetPage(@PathVariable String exerciseId, @PathVariable String sheetId, Model model) {
        metricsService.registerAccess();
        String label = ctx
            .select(SHEETS.LABEL)
            .from(SHEETS)
            .where(SHEETS.EXERCISE.eq(exerciseId), SHEETS.ID.eq(sheetId))
            .fetchOptional(Record1::value1)
            .orElseThrow(NotFoundException::new);
        model.addAttribute(new EditSheetForm(label));
        model.addAttribute("assignments", ctx.fetch(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId)));
        return "exercise/edit-sheet";
    }

    @PostMapping("/sheets/{sheetId}/edit")
    public String editSheet(@PathVariable String exerciseId, @PathVariable String sheetId, @Valid EditSheetForm editSheetForm, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!bindingResult.hasErrors()) {
            SheetRecord sheetRecord = ctx
                .fetchOptional(SHEETS, SHEETS.EXERCISE.eq(exerciseId), SHEETS.ID.eq(sheetId))
                .orElseThrow(NotFoundException::new);
            sheetRecord.setLabelIfChanged(editSheetForm.label);
            if (sheetRecord.changed()) {
                sheetRecord.update();
                log.info("Sheet {} has been changed by assistant {}", sheetRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/admin/sheets/{sheetId}/edit";
        }
        return "exercise/edit-sheet";
    }

    @PostMapping("/sheets/{sheetId}/delete")
    public String deleteSheet(@PathVariable String exerciseId, @PathVariable String sheetId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        // TODO: Translations
        try {
            if (ctx
                .deleteFrom(SHEETS)
                .where(SHEETS.EXERCISE.eq(exerciseId), SHEETS.ID.eq(sheetId))
                .execute() == 1
            ) {
                addRedirectMessage(MessageType.SUCCESS, "Übungsblatt " + sheetId + " wurde gelöscht.", redirectAttributes);
            } else {
                addRedirectMessage(MessageType.ERROR, "Übungsblatt " + sheetId + " konnte nicht gelöscht werden.", redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, "Übungsblatt " + sheetId + " konnte aufgrund von vorhandenen Daten nicht gelöscht werden.\n" + e, redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}";
    }

    @GetMapping("/sheets/{sheetId}/assignments/create")
    public String getCreateAssignmentPage(@PathVariable String exerciseId, @PathVariable String sheetId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateAssignmentForm("", "", "", false));
        return "exercise/create-assignment";
    }

    @PostMapping("/sheets/{sheetId}/assignments/create")
    public String createAssignment(@PathVariable String exerciseId, @PathVariable String sheetId, @Valid CreateAssignmentForm createAssignmentForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        BigDecimal maxPoints = BigDecimal.ZERO;
        try {
            maxPoints = parseMaxPoints(createAssignmentForm.maxPoints);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxPoints", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            AssignmentRecord assignmentRecord = ctx.newRecord(ASSIGNMENTS);
            assignmentRecord.setExerciseId(exerciseId);
            assignmentRecord.setSheetId(sheetId);
            assignmentRecord.setAssignmentId(createAssignmentForm.assignmentId);
            assignmentRecord.setLabel(createAssignmentForm.label);
            assignmentRecord.setMaxpoints(maxPoints);
            assignmentRecord.setShowStatistics(createAssignmentForm.showStatistics);
            try {
                assignmentRecord.insert();
                log.info("Assignment {} has been created by assistant {}", assignmentRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/admin/sheets/{sheetId}/edit";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("assignmentId", "Unique");
            }
        }
        return "exercise/create-assignment";
    }

    @GetMapping("/sheets/{sheetId}/assignments/{assignmentId}/edit")
    public String getEditAssignmentPage(@PathVariable String exerciseId, @PathVariable String sheetId, @PathVariable String assignmentId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(ctx
            .select(
                ASSIGNMENTS.LABEL,
                ASSIGNMENTS.MAXPOINTS.convertFrom(BigDecimal::toString),
                ASSIGNMENTS.SHOWSTATISTICS
            )
            .from(ASSIGNMENTS)
            .where(
                ASSIGNMENTS.EXERCISE.eq(exerciseId),
                ASSIGNMENTS.SHEET.eq(sheetId),
                ASSIGNMENTS.ID.eq(assignmentId)
            )
            .fetchOptional(Records.mapping(EditAssignmentForm::new))
            .orElseThrow(NotFoundException::new)
        );
        return "exercise/edit-assignment";
    }

    @PostMapping("/sheets/{sheetId}/assignments/{assignmentId}/edit")
    public String editAssignment(@PathVariable String exerciseId, @PathVariable String sheetId, @PathVariable String assignmentId, @Valid EditAssignmentForm editAssignmentForm, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        BigDecimal maxPoints = BigDecimal.ZERO;
        try {
            maxPoints = parseMaxPoints(editAssignmentForm.maxPoints);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxPoints", "Invalid");
        }
        AssignmentRecord assignmentRecord = ctx
            .fetchOptional(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId), ASSIGNMENTS.ID.eq(assignmentId))
            .orElseThrow(NotFoundException::new);
        if (!bindingResult.hasErrors()) {
            assignmentRecord.setLabelIfChanged(editAssignmentForm.label);
            assignmentRecord.setMaxpointsIfChanged(maxPoints);
            assignmentRecord.setShowStatisticsIfChanged(editAssignmentForm.showStatistics);
            if (assignmentRecord.changed()) {
                assignmentRecord.update();
                log.info("Assignment {} has been changed by assistant {}", assignmentRecord, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/admin/sheets/{sheetId}/edit";
        }
        return "exercise/edit-assignment";
    }

    @PostMapping("/sheets/{sheetId}/assignments/{assignmentId}/delete")
    public String deleteAssignment(@PathVariable String exerciseId, @PathVariable String sheetId, @PathVariable String assignmentId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        // TODO: Translations
        try {
            if (ctx
                .deleteFrom(ASSIGNMENTS)
                .where(ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId), ASSIGNMENTS.ID.eq(assignmentId))
                .execute() == 1
            ) {
                addRedirectMessage(MessageType.SUCCESS, "Aufgabe " + assignmentId + " wurde gelöscht.", redirectAttributes);
            } else {
                addRedirectMessage(MessageType.ERROR, "Aufgabe " + assignmentId + " konnte nicht gelöscht werden.", redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, "Aufgabe " + assignmentId + " konnte aufgrund von vorhandenen Daten nicht gelöscht werden.\n" + e, redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/admin/sheets/{sheetId}/edit";
    }

    @GetMapping("/registrations")
    public String getRegistrationsPage(@PathVariable String exerciseId, Model model) throws IOException, InterruptedException {
        metricsService.registerAccess();

        // Load groupIds
        List<String> groupIds = ctx
            .select(GROUPS.GROUPID)
            .from(GROUPS)
            .where(GROUPS.EXERCISEID.eq(exerciseId))
            .fetch(Record1::value1);
        groupIds.sort(Comparators.IDENTIFIER);
        Map<Integer, Map<String, GroupPreferenceOption>> groupPreferences = new TreeMap<>();
        try (Cursor<Record3<Integer, String, GroupPreferenceOption>> cursor =
                 ctx
                     .select(GROUPPREFERENCES.USERID, GROUPPREFERENCES.GROUPID, GROUPPREFERENCES.PREFERENCE)
                     .from(GROUPPREFERENCES)
                     .where(GROUPPREFERENCES.EXERCISEID.eq(exerciseId))
                     .fetchLazy()
        ) {
            for (Record3<Integer, String, GroupPreferenceOption> r : cursor) {
                groupPreferences
                    .computeIfAbsent(r.value1(), userId -> new HashMap<>())
                    .put(r.value2(), r.value3());
            }
        }
        Map<Integer, List<Integer>> teamPreferences = new TreeMap<>();
        try (Cursor<Record2<Integer, Integer>> cursor =
                 ctx
                     .select(TEAMPREFERENCES.USERID, TEAMPREFERENCES.FRIEND_USERID)
                     .from(TEAMPREFERENCES)
                     .where(TEAMPREFERENCES.EXERCISEID.eq(exerciseId))
                     .fetchLazy()
        ) {
            for (Record2<Integer, Integer> r : cursor) {
                int userId = r.value1();
                int friendUserId = r.value2();
                if (groupPreferences.keySet().containsAll(Set.of(userId, friendUserId))) {
                    teamPreferences
                        .computeIfAbsent(userId, ignored -> new ArrayList<>())
                        .add(friendUserId);
                }
            }
        }
        teamPreferences.forEach((userId, friendUserIds) -> friendUserIds.sort(null));
        int maxFriends = teamPreferences.values().stream().mapToInt(List::size).max().orElse(0);

        Students s = STUDENTS.as("s");
        Users u = s.user().as("u");
        List<GroupPreferenceOption> missingGroupPreferences = Arrays.asList(new GroupPreferenceOption[groupIds.size()]);
        Object[] data = ctx
            .select(s.GROUPID, s.TEAMID, u.USERID, u.STUDENTID, u.FIRSTNAME, u.LASTNAME, u.EMAIL)
            .from(s)
            .where(s.EXERCISEID.eq(exerciseId))
            .fetch(r -> new Object[]{
                r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7(),
                teamPreferences.getOrDefault(r.value3(), Collections.emptyList()),
                Optional.ofNullable(groupPreferences.get(r.value3()))
                    .map(gp -> groupIds.stream().map(gp::get).toList())
                    .orElse(missingGroupPreferences)
            })
            .toArray();

        model.addAttribute("groupIds", groupIds);
        model.addAttribute("groupIdsJson", JsonUtils.toJson(groupIds));
        model.addAttribute("maxFriends", maxFriends);
        model.addAttribute("data", JsonUtils.toJson(data));

        return "exercise/registrations";
    }

    @GetMapping("/optimus")
    public String getOptimusPage(@PathVariable String exerciseId, Model model, RedirectAttributes redirectAttributes) throws IOException, InterruptedException {
        metricsService.registerAccess();

        // Load groupIds
        List<String> groupIds = ctx
            .select(GROUPS.GROUPID)
            .from(GROUPS)
            .where(GROUPS.EXERCISEID.eq(exerciseId))
            .fetch(Record1::value1);
        if (groupIds.size() < 2) {
            addRedirectMessage(MessageType.ERROR, "Der Optimus-Zuteilungsalgorithmus benötigt mindestens zwei Übungsgruppen.", redirectAttributes);
            return "redirect:/exercise/{exerciseId}/admin/groups";
        }
        groupIds.sort(Comparators.IDENTIFIER);
        Map<Integer, Map<String, GroupPreferenceOption>> groupPreferences = new TreeMap<>();
        try (Cursor<Record3<Integer, String, GroupPreferenceOption>> cursor =
                 ctx
                     .select(GROUPPREFERENCES.USERID, GROUPPREFERENCES.GROUPID, GROUPPREFERENCES.PREFERENCE)
                     .from(GROUPPREFERENCES)
                     .where(GROUPPREFERENCES.EXERCISEID.eq(exerciseId))
                     .fetchLazy()
        ) {
            for (Record3<Integer, String, GroupPreferenceOption> r : cursor) {
                groupPreferences
                    .computeIfAbsent(r.value1(), userId -> new HashMap<>())
                    .put(r.value2(), r.value3());
            }
        }
        if (groupPreferences.isEmpty()) {
            addRedirectMessage(MessageType.ERROR, "Der Optimus-Zuteilungsalgorithmus benötigt mindestens einen Studenten mit angegebenen Gruppenpräferenzen.", redirectAttributes);
            return "redirect:/exercise/{exerciseId}/admin/groups";
        }
        Map<Integer, List<Integer>> teamPreferences = new TreeMap<>();
        try (Cursor<Record2<Integer, Integer>> cursor =
                 ctx
                     .select(TEAMPREFERENCES.USERID, TEAMPREFERENCES.FRIEND_USERID)
                     .from(TEAMPREFERENCES)
                     .where(TEAMPREFERENCES.EXERCISEID.eq(exerciseId))
                     .fetchLazy()
        ) {
            for (Record2<Integer, Integer> r : cursor) {
                int userId = r.value1();
                int friendUserId = r.value2();
                if (groupPreferences.keySet().containsAll(Set.of(userId, friendUserId))) {
                    teamPreferences
                        .computeIfAbsent(userId, ignored -> new ArrayList<>())
                        .add(friendUserId);
                }
            }
        }
        teamPreferences.forEach((userId, friendUserIds) -> friendUserIds.sort(null));
        int maxFriends = teamPreferences.values().stream().mapToInt(List::size).max().orElse(0);
        Students s = STUDENTS.as("s");
        Users u = s.user().as("u");
        Result<Record5<Integer, String, String, String, String>> students = ctx
            .select(u.USERID, u.STUDENTID, u.FIRSTNAME, u.LASTNAME, u.EMAIL)
            .from(s)
            .where(s.EXERCISEID.eq(exerciseId))
            .fetch();

        // TODO: At this point, commit the transaction such that there is no deadlock while lp_solve computes the assignment

        Map<Integer, String> assignment = optimus.calculateAssignment(groupIds, groupPreferences, teamPreferences);

        List<GroupPreferenceOption> missingGroupPreferences = Arrays.asList(new GroupPreferenceOption[groupIds.size()]);
        Object[] data = students.stream()
            .map(r -> new Object[]{
                assignment.get(r.value1()),
                null,
                r.value1(), r.value2(), r.value3(), r.value4(), r.value5(),
                teamPreferences.getOrDefault(r.value1(), Collections.emptyList()),
                Optional.ofNullable(groupPreferences.get(r.value1()))
                    .map(gp -> groupIds.stream().map(gp::get).toList())
                    .orElse(missingGroupPreferences)
            })
            .toArray();

        String importCsv = Arrays.stream(data)
            .flatMap(row1 -> {
                Object[] row = (Object[]) row1;
                return row[0] == null ? Stream.empty() : Stream.of(row[2] + ";" + row[0]);
            })
            .collect(Collectors.joining(System.lineSeparator()));

        model.addAttribute("groupIds", groupIds);
        model.addAttribute("groupIdsJson", JsonUtils.toJson(groupIds));
        model.addAttribute("maxFriends", maxFriends);
        model.addAttribute("data", JsonUtils.toJson(data));
        model.addAttribute("importCsv", importCsv);

        return "exercise/optimus";
    }

    @GetMapping("/import")
    public String getImportStudentsPage(@PathVariable String exerciseId) {
        metricsService.registerAccess();
        return "exercise/import-students";
    }

    @PostMapping("/import")
    @Transactional
    public String importStudents(
        @PathVariable String exerciseId,
        @RequestParam String studentKey,
        @RequestParam String studentsCsv,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        metricsService.registerAccess();
        if (!Set.of("studentId", "username", "userId").contains(studentKey)) {
            throw new IllegalArgumentException("unknown studentKey");
        }

        Set<String> groupIds = ctx
            .select(GROUPS.GROUPID)
            .from(GROUPS)
            .where(GROUPS.EXERCISEID.eq(exerciseId))
            .fetchSet(Record1::value1);

        Map<String, GroupAndTeam> imports = new LinkedHashMap<>();
        List<String> invalidFormat = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> invalidGroup = new ArrayList<>();

        studentsCsv.lines().forEachOrdered(line -> {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                String[] parts = trimmedLine.split(";", 3);
                String key = parts[0].trim();
                if (key.isEmpty()) {
                    invalidFormat.add(trimmedLine);
                } else {
                    imports.compute(key, (k, groupAndTeam) -> {
                        if (groupAndTeam != null) {
                            duplicates.add(trimmedLine);
                            return groupAndTeam;
                        }
                        String groupId = null;
                        String teamId = null;
                        if (parts.length >= 2) {
                            groupId = StringUtils.defaultIfEmpty(parts[1].trim(), null);
                            if (parts.length == 3) {
                                teamId = StringUtils.defaultIfEmpty(parts[2].trim(), null);
                                if (groupId == null && teamId != null) {
                                    invalidFormat.add(trimmedLine);
                                }
                            }
                        }
                        if (groupId != null && !groupIds.contains(groupId)) {
                            invalidGroup.add(trimmedLine);
                        }
                        return new GroupAndTeam(groupId, teamId);
                    });
                }
            }
        });

        Set<String> invalidKeys = Collections.emptySet();
        if (invalidFormat.isEmpty() && duplicates.isEmpty() && invalidGroup.isEmpty()) {
            if (!imports.isEmpty()) {
                Field<String> keyField = switch (studentKey) {
                    case "studentId" -> USERS.STUDENTID;
                    case "username" -> USERS.USERNAME;
                    case "userId" -> USERS.USERID.cast(String.class);
                    default -> throw new IllegalStateException("unknown studentKey");
                };
                Map<String, Integer> userIdMap = ctx
                    .select(keyField, USERS.USERID)
                    .from(USERS)
                    .where(keyField.in(imports.keySet()))
                    .fetchMap(Record2::value1, Record2::value2);
                invalidKeys = new LinkedHashSet<>(imports.keySet());
                invalidKeys.removeAll(userIdMap.keySet());
                if (invalidKeys.isEmpty()) {
                    Map<Integer, StudentRecord> students = ctx
                        .selectFrom(STUDENTS)
                        .where(
                            STUDENTS.EXERCISEID.eq(exerciseId),
                            STUDENTS.USERID.in(userIdMap.values())
                        )
                        .fetchMap(StudentRecord::getUserId);

                    imports.forEach((key, groupAndTeam) -> {
                        StudentRecord studentRecord = students.computeIfAbsent(userIdMap.get(key), userId -> {
                            StudentRecord record = ctx.newRecord(STUDENTS);
                            record.setUserId(userId);
                            record.setExerciseId(exerciseId);
                            return record;
                        });

                        String groupId = groupAndTeam.getGroupId();
                        String teamId = groupAndTeam.getTeamId();
                        if (groupId != null && !groupId.equals(studentRecord.getGroupId())) {
                            // changing group also resets the team
                            studentRecord.setGroupId(groupId);
                            studentRecord.setTeamIdIfChanged(teamId);
                        } else if (teamId != null) {
                            // group stayed the same, but we can change the team
                            studentRecord.setTeamIdIfChanged(teamId);
                        }
                    });
                    ctx.batchStore(students.values()).execute();
                    addRedirectMessage(MessageType.SUCCESS, students.size() + " Studierende importiert!", redirectAttributes);
                    return "redirect:/exercise/{exerciseId}/admin/import";
                }
            }
        }

        for (String line : invalidFormat) {
            addMessage(MessageType.ERROR, "Ungültiges Format: " + line, model);
        }
        for (String line : duplicates) {
            addMessage(MessageType.ERROR, "Doppelter Eintrag: " + line, model);
        }
        for (String line : invalidGroup) {
            addMessage(MessageType.ERROR, "Gruppe existiert nicht: " + line, model);
        }
        for (String key : invalidKeys) {
            addMessage(MessageType.ERROR, "Benutzer existiert nicht: " + key, model);
        }
        model.addAttribute("studentKey", studentKey);
        model.addAttribute("studentsCsv", studentsCsv);
        return "exercise/import-students";
    }

    @Nullable
    public static Integer parseMaxSize(@Nullable String maxSize) throws NumberFormatException {
        if (StringUtils.isNotEmpty(maxSize)) {
            int result = Integer.parseInt(maxSize);
            if (result < 0) {
                throw new NumberFormatException("maxSize cannot be negative");
            }
            return result;
        }
        return null; // no size limit
    }

    public static BigDecimal parseMaxPoints(@Nullable String maxPoints) throws NumberFormatException {
        if (StringUtils.isNotEmpty(maxPoints)) {
            BigDecimal result = new BigDecimal(maxPoints);
            if (result.signum() < 0) {
                throw new NumberFormatException("maxPoints cannot be negative");
            }
            return result;
        }
        return BigDecimal.ZERO;
    }
}
