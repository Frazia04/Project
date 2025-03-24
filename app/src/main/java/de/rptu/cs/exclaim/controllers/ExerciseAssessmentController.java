package de.rptu.cs.exclaim.controllers;

import com.google.common.base.Suppliers;
import de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import de.rptu.cs.exclaim.data.GroupAndTeam;
import de.rptu.cs.exclaim.data.MDAnnotation;
import de.rptu.cs.exclaim.data.SheetWithMaxPoints;
import de.rptu.cs.exclaim.data.StudentWithSheetResults;
import de.rptu.cs.exclaim.data.StudentWithSheetResults.SheetResult;
import de.rptu.cs.exclaim.data.TeamResultData;
import de.rptu.cs.exclaim.data.interfaces.ISheet;
import de.rptu.cs.exclaim.data.interfaces.IStudent;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.AssignmentRecord;
import de.rptu.cs.exclaim.data.records.StudentRecord;
import de.rptu.cs.exclaim.data.records.StudentResultRecord;
import de.rptu.cs.exclaim.data.records.TeamResultAssignmentRecord;
import de.rptu.cs.exclaim.data.records.TeamResultRecord;
import de.rptu.cs.exclaim.data.records.TestResultRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.enums.Attendance;
import de.rptu.cs.exclaim.schema.tables.Annotations;
import de.rptu.cs.exclaim.schema.tables.Assignments;
import de.rptu.cs.exclaim.schema.tables.Sheets;
import de.rptu.cs.exclaim.schema.tables.Studentresults;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.schema.tables.Teamresults;
import de.rptu.cs.exclaim.schema.tables.TeamresultsAssignment;
import de.rptu.cs.exclaim.schema.tables.Testresult;
import de.rptu.cs.exclaim.schema.tables.Uploads;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExerciseRoles;
import de.rptu.cs.exclaim.utils.Comparators;
import de.rptu.cs.exclaim.utils.JsonUtils;
import de.rptu.cs.exclaim.utils.Markdown;
import de.rptu.cs.exclaim.utils.UploadManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.IOUtils;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Record6;
import org.jooq.Record7;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.ID_REGEX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Annotations.ANNOTATIONS;
import static de.rptu.cs.exclaim.schema.tables.Assignments.ASSIGNMENTS;
import static de.rptu.cs.exclaim.schema.tables.Groups.GROUPS;
import static de.rptu.cs.exclaim.schema.tables.Sheets.SHEETS;
import static de.rptu.cs.exclaim.schema.tables.Studentresults.STUDENTRESULTS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Teamresults.TEAMRESULTS;
import static de.rptu.cs.exclaim.schema.tables.TeamresultsAssignment.TEAMRESULTS_ASSIGNMENT;
import static de.rptu.cs.exclaim.schema.tables.Testresult.TESTRESULT;
import static de.rptu.cs.exclaim.schema.tables.Unread.UNREAD;
import static de.rptu.cs.exclaim.schema.tables.Uploads.UPLOADS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Controller
@RequestMapping("/exercise/{exerciseId}")
@RequiredArgsConstructor
@Slf4j
public class ExerciseAssessmentController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;
    private final UploadManager uploadManager;

    @ModelAttribute
    public ExerciseRoles exerciseRoles(@PathVariable String exerciseId) {
        ExerciseRoles exerciseRoles = accessChecker.getExerciseRoles(exerciseId);
        log.debug("Accessing exercise {} with {}", exerciseId, exerciseRoles);
        if (!exerciseRoles.canAssess()) {
            throw new AccessDeniedException("No permission to assess exercise " + exerciseId);
        }
        return exerciseRoles;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Results

    @GetMapping("/results")
    public String getResultsPage(@PathVariable String exerciseId, Model model, ExerciseRoles exerciseRoles) {
        metricsService.registerAccess();
        Sheets s = SHEETS.as("s");
        Assignments a = ASSIGNMENTS.as("a");
        List<SheetWithMaxPoints> sheets = ctx
            .select(s, DSL.sum(a.MAXPOINTS).as("maxpoints"))
            .from(s)
            .leftJoin(a).onKey(Keys.FK__ASSIGNMENTS__SHEETS)
            .where(s.EXERCISE.eq(exerciseId))
            .groupBy(s)
            .fetch(Records.mapping(SheetWithMaxPoints::new));
        sheets.sort(Comparator.comparing(SheetWithMaxPoints::getSheetId, Comparators.IDENTIFIER));
        BigDecimal maxPointsTotal = BigDecimal.ZERO;
        for (SheetWithMaxPoints sheet : sheets) {
            maxPointsTotal = maxPointsTotal.add(sheet.getMaxPoints());
        }

        boolean isAssistant = exerciseRoles.getIsAssistant();
        Students stud = STUDENTS.as("stud");
        Users u = stud.user().as("u");
        Studentresults sr = STUDENTRESULTS.as("sr");
        Teamresults tr = TEAMRESULTS.as("tr");
        TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
        List<StudentWithSheetResults> students = ctx
            .select(
                stud,
                u,
                DSL.multisetAgg(
                        s.ID,
                        sr.GROUPID,
                        sr.TEAMID,
                        // teampoints
                        DSL.field(DSL
                            .select(DSL.sum(tra.POINTS))
                            .from(tra)
                            .where(tra.EXERCISE.eq(tr.EXERCISE), tra.SHEET.eq(tr.SHEET), tra.GROUPID.eq(tr.GROUPID), tra.TEAMID.eq(tr.TEAMID))
                        ),
                        sr.DELTAPOINTS,
                        tr.HIDEPOINTS,
                        sr.ATTENDED
                    )
                    .convertFrom(r -> r.intoMap(Record7::value1, r2 -> new SheetResult(
                        /* groupId */ r2.value2(),
                        /* teamId */ r2.value3(),
                        /* teampoints */ r2.value4(),
                        /* deltapoints */ r2.value5(),
                        /* hidePoints */ Boolean.TRUE.equals(r2.value6()),
                        /* attended */ r2.value7()
                    ))).as("sheetResults")
            )
            .from(stud)
            .leftJoin(s).on(s.EXERCISE.eq(exerciseId))
            .leftJoin(sr).onKey(Keys.FK__STUDENTRESULTS__SHEETS).and(sr.USERID.eq(u.USERID))
            .leftJoin(tr).onKey(Keys.FK__TEAMRESULTS__SHEETS).and(tr.GROUPID.eq(sr.GROUPID)).and(tr.TEAMID.eq(sr.TEAMID))
            .where(
                stud.EXERCISEID.eq(exerciseId),
                exerciseRoles.applyGroupIdRestriction(stud.GROUPID)
            )
            .groupBy(stud, u)
            .fetch(Records.mapping(StudentWithSheetResults::new));

        // The student data is passed to the template in JSON format (for table rendering via JavaScript).
        // It is an array of arrays: the outer one for rows, the inner one for columns.
        int numSheets = sheets.size();
        Object[] data = students.stream()
            .map(r -> {
                IStudent student = r.getStudent();
                IUser user = r.getUser();
                Map<String, SheetResult> sheetResults = r.getSheetResults();

                BigDecimal[] points = new BigDecimal[numSheets];
                Attendance[] attendance = new Attendance[numSheets];

                int i = 0;
                for (ISheet sheet : sheets) {
                    SheetResult sheetResult = sheetResults.get(sheet.getSheetId());
                    if (sheetResult != null) {
                        points[i] = sheetResult.getPoints();
                        attendance[i] = sheetResult.getAttended();
                    }
                    i++;
                }

                Object[] rowData;
                if (isAssistant) {
                    rowData = new Object[9];
                    rowData[8] = user.getStudentId();
                } else {
                    rowData = new Object[8];
                }
                rowData[0] = user.getUserId();
                rowData[1] = student.getGroupId();
                rowData[2] = student.getTeamId();
                rowData[3] = user.getFirstname();
                rowData[4] = user.getLastname();
                rowData[5] = user.getEmail();
                rowData[6] = points;
                rowData[7] = attendance;
                return rowData;
            })
            .toArray();

        StringBuilder sb = new StringBuilder("mailto:").append(accessChecker.getUser().getEmail());
        boolean first = true;
        for (StudentWithSheetResults student : students) {
            if (first) {
                sb.append("?bcc=");
                first = false;
            } else {
                sb.append(",");
            }
            IUser user = student.getUser();
            sb
                .append('"')
                .append(user.getFirstname())
                .append(' ')
                .append(user.getLastname())
                .append("\" <")
                .append(user.getEmail())
                .append('>');
        }
        String allEmail = sb.toString();

        model.addAttribute("sheets", sheets);
        model.addAttribute("maxPointsTotal", maxPointsTotal);
        model.addAttribute("isAssistant", isAssistant);
        model.addAttribute("allEmail", allEmail);
        model.addAttribute("data", JsonUtils.toJson(data));
        return "exercise/results";
    }

    @GetMapping("/students/{userId}")
    public String getDetailResultsPage(@PathVariable String exerciseId, @PathVariable int userId, Model model, ExerciseRoles exerciseRoles) {
        Students stud = STUDENTS.as("stud");
        Users u = stud.user().as("u");
        Sheets s = SHEETS.as("s");
        Studentresults sr = STUDENTRESULTS.as("sr");
        Teamresults tr = TEAMRESULTS.as("tr");
        TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
        Field<Map<String, SheetResult>> sheetResults = DSL.multisetAgg(
                s.ID,
                sr.GROUPID,
                sr.TEAMID,
                // teampoints
                DSL.field(DSL
                    .select(DSL.sum(tra.POINTS))
                    .from(tra)
                    .where(tra.EXERCISE.eq(tr.EXERCISE), tra.SHEET.eq(tr.SHEET), tra.GROUPID.eq(tr.GROUPID), tra.TEAMID.eq(tr.TEAMID))
                ),
                sr.DELTAPOINTS,
                tr.HIDEPOINTS,
                sr.ATTENDED
            )
            .convertFrom(r -> r.intoMap(Record7::value1, r2 -> new SheetResult(
                /* groupId */ r2.value2(),
                /* teamId */ r2.value3(),
                /* teampoints */ r2.value4(),
                /* deltapoints */ r2.value5(),
                /* hidePoints */ Boolean.TRUE.equals(r2.value6()),
                /* attended */ r2.value7()
            ))).as("sheetResults");
        StudentWithSheetResults studentWithSheetResults = ctx
            .select(stud, u, sheetResults)
            .from(stud)
            .leftJoin(s).on(s.EXERCISE.eq(exerciseId))
            .leftJoin(sr).onKey(Keys.FK__STUDENTRESULTS__SHEETS).and(sr.USERID.eq(u.USERID))
            .leftJoin(tr).onKey(Keys.FK__TEAMRESULTS__SHEETS).and(tr.GROUPID.eq(sr.GROUPID)).and(tr.TEAMID.eq(sr.TEAMID))
            .where(stud.EXERCISEID.eq(exerciseId), stud.USERID.eq(userId))
            .groupBy(stud, u)
            .fetchOne(Records.mapping(StudentWithSheetResults::new));

        if (exerciseRoles.getIsAssistant()) {
            // Ensure that student exists
            if (studentWithSheetResults == null) {
                throw new NotFoundException();
            }

            // We need all group ids for the group select box
            List<String> groupIds = ctx
                .select(GROUPS.GROUPID)
                .from(GROUPS)
                .where(GROUPS.EXERCISEID.eq(exerciseId))
                .fetch(Record1::value1);
            groupIds.sort(Comparators.IDENTIFIER);
            model.addAttribute("groups", groupIds);
        } else {
            // Ensure we have access for that student, not revealing whether a user is a student
            String groupId;
            if (studentWithSheetResults == null
                || (groupId = studentWithSheetResults.getStudent().getGroupId()) == null
                || !exerciseRoles.canAssess(groupId)
            ) {
                throw new AccessDeniedException("No permission to assess student " + userId + " in exercise " + exerciseId);
            }
        }

        model.addAttribute("studentWithSheetResults", studentWithSheetResults);
        return "exercise/results-detail";
    }

    @PostMapping("/students/{userId}")
    public String editStudent(
        @PathVariable String exerciseId,
        @PathVariable int userId,
        ExerciseRoles exerciseRoles,
        @RequestParam(required = false) String groupId,
        @RequestParam String teamId,
        RedirectAttributes redirectAttributes
    ) {
        // Check student existence and access
        StudentRecord studentRecord = ctx.fetchOne(STUDENTS, STUDENTS.USERID.eq(userId), STUDENTS.EXERCISEID.eq(exerciseId));
        if (exerciseRoles.getIsAssistant()) {
            if (studentRecord == null) {
                throw new NotFoundException();
            }
        } else {
            // also set method parameter groupId to current group, as tutors cannot change the group
            if (studentRecord == null
                || (groupId = studentRecord.getGroupId()) == null
                || !exerciseRoles.canAssess(groupId)
            ) {
                throw new AccessDeniedException("No permission to assess student " + userId + " in exercise " + exerciseId);
            }
        }

        // Validate inputs
        if (StringUtils.isEmpty(teamId)) {
            teamId = null;
        }
        if ("-".equals(groupId)) {
            groupId = null;
        }
        if (teamId != null) {
            if (groupId == null) {
                addRedirectMessage(MessageType.ERROR, "Team ohne Gruppe ist nicht möglich!", redirectAttributes);
                return "redirect:/exercise/{exerciseId}/students/{userId}";
            }
            if (!teamId.matches(ID_REGEX) // check team id against regex
                // but allow to keep the team unchanged even if the current id does not match our regex
                && !(groupId.equals(studentRecord.getGroupId()) && teamId.equals(studentRecord.getTeamId()))
            ) {
                // allow to assign the student to any other already existing team that does not match our regex
                boolean isExistingTeam = ctx
                    .select(DSL.field(DSL.or(
                        DSL.exists(DSL
                            .selectOne()
                            .from(STUDENTS)
                            .where(STUDENTS.EXERCISEID.eq(exerciseId), STUDENTS.GROUPID.eq(groupId), STUDENTS.TEAMID.eq(teamId))
                        ),
                        DSL.exists(DSL
                            .selectOne()
                            .from(STUDENTRESULTS)
                            .where(STUDENTRESULTS.EXERCISE.eq(exerciseId), STUDENTRESULTS.GROUPID.eq(groupId), STUDENTRESULTS.TEAMID.eq(teamId))
                        ),
                        DSL.exists(DSL
                            .selectOne()
                            .from(TEAMRESULTS)
                            .where(TEAMRESULTS.EXERCISE.eq(exerciseId), TEAMRESULTS.GROUPID.eq(groupId), TEAMRESULTS.TEAMID.eq(teamId))
                        )
                    )))
                    .fetchSingle(Record1::value1);
                if (!isExistingTeam) {
                    addRedirectMessage(MessageType.ERROR, "Die Team ID '" + teamId + "' entspricht nicht dem erforderlichen Format " + ID_REGEX + "!", redirectAttributes);
                    return "redirect:/exercise/{exerciseId}/students/{userId}";
                }
            }
        }

        studentRecord.setGroupIdIfChanged(groupId);
        studentRecord.setTeamIdIfChanged(teamId);
        if (studentRecord.changed()) {
            studentRecord.update();

            // Update existing studentresult entries that do not have a team (created by setting attendance)
            ctx
                .update(STUDENTRESULTS)
                .set(STUDENTRESULTS.GROUPID, groupId)
                .set(STUDENTRESULTS.TEAMID, teamId)
                .where(
                    STUDENTRESULTS.USERID.eq(userId),
                    STUDENTRESULTS.EXERCISE.eq(exerciseId),
                    STUDENTRESULTS.TEAMID.isNull()
                )
                .execute();

            // TODO: Create missing studentresults for sheets where teamresults already exists

            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }

        return "redirect:/exercise/{exerciseId}/results";
    }

    @PostMapping("/students/{userId}/remove")
    @PreAuthorize("#exerciseRoles.isAssistant")
    public String removeStudentFromExercise(@PathVariable String exerciseId, @PathVariable int userId, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        // TODO: Translations
        try {
            if (ctx
                .deleteFrom(STUDENTS)
                .where(STUDENTS.USERID.eq(userId), STUDENTS.EXERCISEID.eq(exerciseId))
                .execute() == 1
            ) {
                addRedirectMessage(MessageType.SUCCESS, "Benutzer " + userId + " wurde aus der Vorlesung ausgetragen.", redirectAttributes);
            } else {
                addRedirectMessage(MessageType.ERROR, "Benutzer " + userId + " konnte nicht aus der Vorlesung ausgetragen werden.", redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/results";
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, "Benutzer " + userId + " konnte aufgrund von vorhandenen Daten aus der Vorlesung ausgetragen werden.\n" + e, redirectAttributes);
            return "redirect:/exercise/{exerciseId}/students/{userId}";
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Assessment (points)

    @GetMapping("/sheet/{sheetId}/assessment")
    public String getAssessmentPage(@PathVariable String exerciseId, @PathVariable String sheetId, ExerciseRoles exerciseRoles, Model model) {
        metricsService.registerAccess();
        List<AssignmentRecord> assignments = ctx.fetch(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId));
        assignments.sort(Comparator.comparing(AssignmentRecord::getAssignmentId, Comparators.IDENTIFIER));

        Students s = STUDENTS.as("s");
        Assignments a = ASSIGNMENTS.as("a");
        Teamresults tr = TEAMRESULTS.as("tr");
        TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
        Uploads u = UPLOADS.as("u");
        Testresult test = TESTRESULT.as("test");
        Testresult test2 = TESTRESULT.as("test2");

        Map<GroupAndTeam, TeamResultData> result = ctx
            // TODO: Missing test results for teams that do not yet have a teamresults entry
            .select(
                tr.GROUPID,
                tr.TEAMID,
                DSL
                    .multisetAgg(
                        a.ID,
                        tra.POINTS,
                        // filesCount
                        DSL.field(DSL
                            .selectCount()
                            .from(u)
                            .where(
                                u.EXERCISE.eq(exerciseId),
                                u.SHEET.eq(sheetId),
                                u.ASSIGNMENT.eq(a.ID),
                                u.GROUPID.eq(tr.GROUPID),
                                u.TEAMID.eq(tr.TEAMID),
                                u.DELETE_DATE.isNull()
                            )
                        ),
                        test.TESTS_PASSED,
                        test.TESTS_TOTAL,
                        test.REQUESTNR
                    )
                    .convertFrom(res -> res.intoMap(Record6::value1, r -> new TeamResultData.AssignmentResult(
                        /* points */ r.value2(),
                        /* filesCount */ r.value3(),
                        /* testsPassed */ r.value4(),
                        /* testsTotal */ r.value5(),
                        /* testsRequestNr */ r.value6()
                    )))
                    .as("results"),
                tr.HIDECOMMENTS,
                tr.HIDEPOINTS
            )
            .from(tr)
            .leftJoin(a).on(a.EXERCISE.eq(exerciseId), a.SHEET.eq(sheetId))
            .leftJoin(tra).onKey(Keys.FK__TEAMRESULTS_ASSIGNMENT__TEAMRESULTS).and(tra.ASSIGNMENT.eq(a.ID))
            .leftJoin(test).on(
                test.EXERCISE.eq(exerciseId),
                test.SHEET.eq(sheetId),
                test.ASSIGNMENT.eq(a.ID),
                test.GROUPID.eq(tr.GROUPID),
                test.TEAMID.eq(tr.TEAMID),
                // Limit to most recent test
                test.REQUESTNR.eq(DSL
                    .select(DSL.max(test2.REQUESTNR))
                    .from(test2)
                    .where(
                        test2.EXERCISE.eq(exerciseId),
                        test2.SHEET.eq(sheetId),
                        test2.ASSIGNMENT.eq(a.ID),
                        test2.GROUPID.eq(tr.GROUPID),
                        test2.TEAMID.eq(tr.TEAMID)
                    )
                )
            )
            .where(
                tr.EXERCISE.eq(exerciseId),
                tr.SHEET.eq(sheetId),
                exerciseRoles.applyGroupIdRestriction(tr.GROUPID)
            )
            .groupBy(tr.GROUPID, tr.TEAMID, tr.HIDECOMMENTS, tr.HIDEPOINTS)
            .fetchMap(r -> new GroupAndTeam(r.value1(), r.value2()), Records.mapping(TeamResultData::new));

        // Add rows for teams that do not yet have a teamresults entry
        Studentresults sr = STUDENTRESULTS.as("sr");
        ctx
            .selectDistinct(sr.GROUPID, sr.TEAMID)
            .from(sr)
            .where(
                sr.EXERCISE.eq(exerciseId),
                sr.SHEET.eq(sheetId),
                sr.TEAMID.isNotNull(),
                exerciseRoles.applyGroupIdRestriction(sr.GROUPID)
            )
            .union(DSL
                .selectDistinct(s.GROUPID, s.TEAMID)
                .from(s)
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
            .forEach(record -> {
                String groupId = record.value1();
                String teamId = record.value2();
                result.computeIfAbsent(
                    new GroupAndTeam(groupId, teamId),
                    k -> new TeamResultData(
                        groupId, teamId, Collections.emptyMap(), false, false
                    )
                );
            });

        // The data is passed to the template in JSON format (for table rendering via JavaScript).
        // It is an array of arrays: the outer one for rows, the inner one for columns.
        int numAssignments = assignments.size();
        Object[] data = result.values().stream()
            .map(r -> {
                Object[] rowData = new Object[4 + numAssignments];
                rowData[0] = r.getGroupId();
                rowData[1] = r.getTeamId();
                int i = 2;
                Map<String, TeamResultData.AssignmentResult> assignmentResults = r.getAssignmentResults();
                for (AssignmentRecord assignment : assignments) {
                    TeamResultData.AssignmentResult assignmentResult = assignmentResults.get(assignment.getAssignmentId());
                    if (assignmentResult != null) {
                        rowData[i] = assignmentResult.getPoints();
                    }
                    i++;
                }
                rowData[i++] = r.getHideComments();
                rowData[i] = r.getHidePoints();
                return rowData;
            })
            .toArray();

        model.addAttribute("assignments", assignments);
        model.addAttribute("data", JsonUtils.toJson(data));
        return "exercise/assessment";
    }

    @GetMapping("/sheet/{sheetId}/assessment/{groupId}/{teamId}")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    public String getAssessmentTeamPage(@PathVariable String exerciseId, @PathVariable String sheetId, @PathVariable String groupId, @PathVariable String teamId, ExerciseRoles exerciseRoles, Model model) throws IOException {
        metricsService.registerAccess();

        // Existing studentresults entries
        Studentresults sr = STUDENTRESULTS.as("sr");
        Users u = USERS.as("u");
        List<Tuple2<IUser, StudentResultRecord>> students = ctx
            .select(u, sr)
            .from(sr)
            .innerJoin(u).on(u.USERID.eq(sr.USERID))
            .where(
                sr.EXERCISE.eq(exerciseId),
                sr.SHEET.eq(sheetId),
                sr.GROUPID.eq(groupId),
                sr.TEAMID.eq(teamId)
            )
            .fetch(r -> new Tuple2<>(r.value1(), r.value2()));

        // Students currently in the team not having a studentresults entry for that sheet
        Students s = STUDENTS.as("s");
        students.addAll(ctx
            .select(s.user().as("u"))
            .from(s)
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
            .fetch(r -> {
                IUser user = r.value1();
                StudentResultRecord record = ctx.newRecord(STUDENTRESULTS);
                record.setExerciseId(exerciseId);
                record.setSheetId(sheetId);
                record.setUserId(user.getUserId());
                record.setGroupId(groupId);
                record.setTeamId(teamId);
                return new Tuple2<>(user, record);
            })
        );
        if (students.isEmpty()) {
            throw new NotFoundException();
        }

        List<AssignmentRecord> assignments = ctx.fetch(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId));
        assignments.sort(Comparator.comparing(AssignmentRecord::getAssignmentId, Comparators.IDENTIFIER));

        boolean hideComments, hidePoints;
        String comment, commentHtml;
        Map<String, BigDecimal> assignmentPoints;
        {
            Teamresults tr = TEAMRESULTS.as("tr");
            Assignments a = ASSIGNMENTS.as("a");
            TeamresultsAssignment tra = TEAMRESULTS_ASSIGNMENT.as("tra");
            Record4<Boolean, Boolean, String, Map<String, BigDecimal>> teamResult = ctx
                .select(
                    tr.HIDECOMMENTS,
                    tr.HIDEPOINTS,
                    tr.COMMENT,
                    DSL
                        .multisetAgg(a.ID, tra.POINTS)
                        .convertFrom(result -> result.intoMap(Record2::value1, Record2::value2))
                        .as("results")
                )
                .from(tr)
                .leftJoin(a).on(a.EXERCISE.eq(exerciseId), a.SHEET.eq(sheetId))
                .leftJoin(tra).onKey(Keys.FK__TEAMRESULTS_ASSIGNMENT__TEAMRESULTS).and(tra.ASSIGNMENT.eq(a.ID))
                .where(
                    tr.EXERCISE.eq(exerciseId),
                    tr.SHEET.eq(sheetId),
                    tr.GROUPID.eq(groupId),
                    tr.TEAMID.eq(teamId)
                )
                .groupBy(tr.GROUPID, tr.TEAMID, tr.HIDECOMMENTS, tr.HIDEPOINTS, tr.COMMENT)
                .fetchOne();
            if (teamResult != null) {
                hideComments = teamResult.value1();
                hidePoints = teamResult.value2();
                comment = StringUtils.defaultString(teamResult.value3());
                commentHtml = Markdown.toHtml(comment);
                assignmentPoints = teamResult.value4();
            } else {
                hideComments = hidePoints = false;
                comment = commentHtml = "";
                assignmentPoints = Collections.emptyMap();
            }
        }

        // Uploads
        Map<String, Integer> uploadCounts = ctx
            .select(UPLOADS.ASSIGNMENT, DSL.count(UPLOADS.ID))
            .from(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.DELETE_DATE.isNull()
            )
            .groupBy(UPLOADS.ASSIGNMENT)
            .fetchMap(Record2::value1, Record2::value2);

        // Tests
        Testresult test = TESTRESULT.as("test");
        Testresult test2 = TESTRESULT.as("test2");
        Map<String, TestResultRecord> testResults = ctx
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
                        test2.EXERCISE.eq(exerciseId),
                        test2.SHEET.eq(sheetId),
                        test2.ASSIGNMENT.eq(test.ASSIGNMENT),
                        test2.GROUPID.eq(groupId),
                        test2.TEAMID.eq(teamId)
                    )
                )
            )
            .fetchMap(TestResultRecord::getAssignmentId);

        // Feedbacks
        List<String> feedbackUploads = uploadManager.getFeedbackUploads(exerciseId, sheetId, groupId, teamId);

        model.addAttribute("isAssistant", exerciseRoles.getIsAssistant());
        model.addAttribute("students", students);
        model.addAttribute("assignments", assignments);
        model.addAttribute("hideComments", hideComments);
        model.addAttribute("hidePoints", hidePoints);
        model.addAttribute("comment", comment);
        model.addAttribute("commentHtml", commentHtml);
        model.addAttribute("assignmentPoints", assignmentPoints);
        model.addAttribute("uploadCounts", uploadCounts);
        model.addAttribute("testResults", testResults);
        model.addAttribute("feedbackuploads", feedbackUploads);
        return "exercise/assessment-team";
    }

    @PostMapping("/sheet/{sheetId}/assessment/{groupId}/{teamId}")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    @Transactional
    public String assessTeam(@PathVariable String exerciseId, @PathVariable String sheetId, @PathVariable String groupId, @PathVariable String teamId, ExerciseRoles exerciseRoles, @RequestBody MultiValueMap<String, String> formData, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        Studentresults sr = STUDENTRESULTS.as("sr");
        Students s = STUDENTS.as("s");

        // Existing studentresults entries
        List<StudentResultRecord> students = ctx
            .selectFrom(sr)
            .where(
                sr.EXERCISE.eq(exerciseId),
                sr.SHEET.eq(sheetId),
                sr.GROUPID.eq(groupId),
                sr.TEAMID.eq(teamId)
            )
            .forUpdate()
            .fetch();

        // Students currently in the team not having a studentresults entry for that sheet
        students.addAll(ctx
            .select(s.USERID)
            .from(s)
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
            .fetch(r -> {
                int userId = r.value1();
                StudentResultRecord record = ctx.newRecord(STUDENTRESULTS);
                record.setExerciseId(exerciseId);
                record.setSheetId(sheetId);
                record.setUserId(userId);
                record.setGroupId(groupId);
                record.setTeamId(teamId);
                return record;
            })
        );
        if (students.isEmpty()) {
            throw new NotFoundException();
        }
        List<String> errors = new ArrayList<>();

        // Delta points
        for (StudentResultRecord student : students) {
            int userId = student.getUserId();
            String delta = formData.getFirst("delta-" + userId);
            if (delta != null) {
                BigDecimal value;
                if (delta.isEmpty()) {
                    value = null;
                } else {
                    try {
                        value = new BigDecimal(delta);
                    } catch (NumberFormatException e) {
                        errors.add("Deltapunkte für Benutzer " + userId + " ungültig: " + delta);
                        continue;
                    }
                    if (value.compareTo(BigDecimal.ZERO) == 0) {
                        value = null;
                    }
                }
                student.setDeltapointsIfChanged(value);
            }
            String reason = formData.getFirst("reason-" + userId);
            if (reason != null) {
                student.setDeltapointsReasonIfChanged(reason.isEmpty() ? null : reason);
            }
        }
        ctx.batchStore(students).execute();

        // Comment and meta-data (must be stored before assignment points)
        TeamResultRecord result = ctx
            .selectFrom(TEAMRESULTS)
            .where(
                TEAMRESULTS.EXERCISE.eq(exerciseId),
                TEAMRESULTS.SHEET.eq(sheetId),
                TEAMRESULTS.GROUPID.eq(groupId),
                TEAMRESULTS.TEAMID.eq(teamId)
            )
            .forUpdate()
            .fetchOptional()
            .orElseGet(() -> {
                TeamResultRecord record = ctx.newRecord(TEAMRESULTS);
                record.setExerciseId(exerciseId);
                record.setSheetId(sheetId);
                record.setGroupId(groupId);
                record.setTeamId(teamId);
                return record;
            });
        String comment = formData.getFirst("comment");
        if (comment != null) {
            result.setCommentIfChanged(comment.isEmpty() ? null : comment);
        }
        result.setHideCommentsIfChanged("on".equals(formData.getFirst("hidecomments")));
        result.setHidePointsIfChanged("on".equals(formData.getFirst("hidepoints")));
        result.store();

        // Points for assignments
        List<AssignmentRecord> assignments = ctx.fetch(ASSIGNMENTS, ASSIGNMENTS.EXERCISE.eq(exerciseId), ASSIGNMENTS.SHEET.eq(sheetId));
        assignments.sort(Comparator.comparing(AssignmentRecord::getAssignmentId, Comparators.IDENTIFIER));
        Map<String, TeamResultAssignmentRecord> assignmentResults = ctx
            .selectFrom(TEAMRESULTS_ASSIGNMENT)
            .where(
                TEAMRESULTS_ASSIGNMENT.EXERCISE.eq(exerciseId),
                TEAMRESULTS_ASSIGNMENT.SHEET.eq(sheetId),
                TEAMRESULTS_ASSIGNMENT.GROUPID.eq(groupId),
                TEAMRESULTS_ASSIGNMENT.TEAMID.eq(teamId)
            )
            .forUpdate()
            .fetchMap(TeamResultAssignmentRecord::getAssignmentId);
        for (AssignmentRecord assignment : assignments) {
            String assignmentId = assignment.getAssignmentId();
            String points = formData.getFirst("asgn-" + assignmentId);
            if (points != null) {
                BigDecimal value;
                if (points.isEmpty()) {
                    value = null;
                } else {
                    try {
                        value = new BigDecimal(points);
                    } catch (NumberFormatException e) {
                        errors.add("Punkte für Aufgabe " + assignmentId + " ungültig: " + points);
                        continue;
                    }
                    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(assignment.getMaxpoints()) > 0) {
                        errors.add("Punkte für Aufgabe " + assignmentId + " ungültig: " + points);
                        continue;
                    }
                }
                assignmentResults
                    .computeIfAbsent(assignmentId, ignored -> {
                        TeamResultAssignmentRecord record = ctx.newRecord(TEAMRESULTS_ASSIGNMENT);
                        record.setExerciseId(exerciseId);
                        record.setSheetId(sheetId);
                        record.setAssignmentId(assignmentId);
                        record.setGroupId(groupId);
                        record.setTeamId(teamId);
                        return record;
                    })
                    .setPointsIfChanged(points.isEmpty() ? null : new BigDecimal(points));
            }
        }
        ctx.batchStore(assignmentResults.values()).execute();

        if (errors.isEmpty()) {
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            if (formData.containsKey("save-continue")) {
                // Find next team
                List<GroupAndTeam> teams = ctx
                    .selectDistinct(sr.GROUPID, sr.TEAMID)
                    .from(sr)
                    .where(
                        sr.EXERCISE.eq(exerciseId),
                        sr.SHEET.eq(sheetId),
                        exerciseRoles.applyGroupIdRestriction(sr.GROUPID)
                    )
                    .union(DSL
                        .selectDistinct(s.GROUPID, s.TEAMID)
                        .from(s)
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
                    .fetch(r -> new GroupAndTeam(r.value1(), r.value2()));
                teams.sort(Comparator
                    .comparing(GroupAndTeam::getGroupId, Comparators.IDENTIFIER)
                    .thenComparing(GroupAndTeam::getTeamId, Comparators.IDENTIFIER)
                );
                Iterator<GroupAndTeam> it = teams.iterator();

                GroupAndTeam next = new GroupAndTeam(groupId, teamId);
                while (it.hasNext()) {
                    GroupAndTeam team = it.next();
                    if (groupId.equals(team.getGroupId()) && teamId.equals(team.getTeamId())) {
                        next = it.hasNext() ? it.next() : teams.get(0);
                        break;
                    }
                }
                return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/assessment/" + next.getGroupId() + "/" + next.getTeamId();
            }
        } else {
            for (String error : errors) {
                addRedirectMessage(MessageType.ERROR, error, redirectAttributes);
            }
            addRedirectMessage(MessageType.WARNING, "Die übrigen Änderungen wurden gespeichert.", redirectAttributes);
        }

        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/assessment/{groupId}/{teamId}";
    }

    @PostMapping("/sheet/{sheetId}/assessment/{groupId}/{teamId}/feedback")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    public String postUploadFeedback(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @RequestParam MultipartFile file,
        ExerciseRoles exerciseRoles
    ) throws IOException {
        metricsService.registerAccess();
        String filename = file.getOriginalFilename();
        if (StringUtils.isEmpty(filename)) {
            throw new IllegalArgumentException("Keine Datei angegeben");
        }

        Path destination = uploadManager.getFeedbackUploadFolder(exerciseId, sheetId, groupId, teamId);
        destination.toFile().mkdirs();
        file.transferTo(destination.resolve(filename));

        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/assessment/{groupId}/{teamId}";
    }

    @PostMapping("/sheet/{sheetId}/assessment/{groupId}/{teamId}/feedback/{filename}/delete")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    public String deleteFeedback(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable String filename,
        ExerciseRoles exerciseRoles
    ) throws IOException {
        metricsService.registerAccess();
        Path feedbackFolder = uploadManager.getFeedbackUploadFolder(exerciseId, sheetId, groupId, teamId);
        try (Stream<Path> files = Files.walk(feedbackFolder, 1)) {
            for (Path path : files.filter(Files::isRegularFile).toArray(Path[]::new)) {
                if (path.getFileName().toString().equals(filename)) {
                    Files.delete(path);
                }
            }
        }
        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/assessment/{groupId}/{teamId}";
    }

    @PostMapping("/sheet/{sheetId}/publish-assessment")
    public String publishCommentsAndPoints(@PathVariable String exerciseId, @PathVariable String sheetId, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ctx
            .update(TEAMRESULTS)
            .set(TEAMRESULTS.HIDECOMMENTS, false)
            .set(TEAMRESULTS.HIDEPOINTS, false)
            .where(
                TEAMRESULTS.EXERCISE.eq(exerciseId),
                TEAMRESULTS.SHEET.eq(sheetId),
                exerciseRoles.applyGroupIdRestriction(TEAMRESULTS.GROUPID)
            )
            .execute();
        addRedirectMessage(MessageType.SUCCESS, "Bewertung wurden veröffentlicht.", redirectAttributes);
        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/assessment";
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Attendance

    @GetMapping("/sheet/{sheetId}/attendance")
    public String getAttendancePage(@PathVariable String exerciseId, @PathVariable String sheetId, ExerciseRoles exerciseRoles, Model model) {
        metricsService.registerAccess();
        boolean isAssistant = exerciseRoles.getIsAssistant();
        Object[] data;
        Students stud = STUDENTS.as("stud");
        Users u = stud.user().as("u");
        Studentresults sr = STUDENTRESULTS.as("sr");
        if (isAssistant) {
            data = ctx
                .select(
                    u.USERID,
                    stud.GROUPID,
                    stud.TEAMID,
                    u.FIRSTNAME,
                    u.LASTNAME,
                    sr.ATTENDED.convertFrom(attendance -> attendance == null ? null : attendance.name()),
                    u.STUDENTID
                )
                .from(stud)
                .leftJoin(sr).onKey(Keys.FK__STUDENTRESULTS__STUDENTS).and(sr.SHEET.eq(sheetId))
                .where(stud.EXERCISEID.eq(exerciseId))
                .fetchArrays();
        } else {
            data = ctx
                .select(
                    u.USERID,
                    stud.GROUPID,
                    stud.TEAMID,
                    u.FIRSTNAME,
                    u.LASTNAME,
                    sr.ATTENDED.convertFrom(attendance -> attendance == null ? null : attendance.name())
                )
                .from(stud)
                .leftJoin(sr).onKey(Keys.FK__STUDENTRESULTS__STUDENTS).and(sr.SHEET.eq(sheetId))
                .where(
                    stud.EXERCISEID.eq(exerciseId),
                    stud.GROUPID.in(exerciseRoles.getTutorGroups())
                )
                .fetchArrays();
        }

        model.addAttribute("isAssistant", isAssistant);
        model.addAttribute("data", JsonUtils.toJson(Objects.requireNonNull(data)));
        return "exercise/attendance";
    }

    @PostMapping("/sheet/{sheetId}/attendance")
    public String saveAttendance(@PathVariable String exerciseId, @PathVariable String sheetId, @RequestBody MultiValueMap<String, String> formData, ExerciseRoles exerciseRoles, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        Map<Integer, Attendance> attendance = new HashMap<>();
        Pattern pattern = Pattern.compile("user-(\\d+)");
        for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
            Matcher matcher = pattern.matcher(entry.getKey());
            if (matcher.matches()) {
                int userId = Integer.parseInt(matcher.group(1));
                String attendanceString = entry.getValue().get(0);
                attendance.put(userId, "-".equals(attendanceString) ? null : Attendance.valueOf(attendanceString));
            }
        }

        // Retrieve existing data
        Studentresults sr = STUDENTRESULTS.as("sr");
        Map<Integer, StudentResultRecord> records = ctx
            .selectFrom(sr)
            .where(
                sr.EXERCISE.eq(exerciseId),
                sr.SHEET.eq(sheetId),
                sr.USERID.in(attendance.keySet()),
                exerciseRoles.applyGroupIdRestriction(() -> sr.student().as("stud").GROUPID)
            )
            .forUpdate()
            .fetchMap(StudentResultRecord::getUserId);

        // Maybe we need the current group and team of some students
        Students s = STUDENTS.as("s");
        Supplier<Map<Integer, GroupAndTeam>> students = Suppliers.memoize(() -> ctx
            .selectFrom(s)
            .where(
                s.EXERCISEID.eq(exerciseId),
                s.USERID.in(attendance.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .map(Map.Entry::getKey)
                    .toArray(Integer[]::new)
                ),
                exerciseRoles.applyGroupIdRestriction(s.GROUPID)
            )
            .fetchMap(StudentRecord::getUserId, GroupAndTeam::new)
        );

        // Perform updates
        for (Map.Entry<Integer, Attendance> entry : attendance.entrySet()) {
            Attendance attended = entry.getValue();
            records.compute(entry.getKey(), (userId, record) -> {
                if (record == null) {
                    if (attended != null) {
                        GroupAndTeam groupAndTeam = students.get().get(userId);
                        if (groupAndTeam != null) {
                            record = ctx.newRecord(STUDENTRESULTS);
                            record.setExerciseId(exerciseId);
                            record.setSheetId(sheetId);
                            record.setUserId(userId);
                            record.setGroupId(groupAndTeam.getGroupId());
                            record.setTeamId(groupAndTeam.getTeamId());
                            record.setAttended(attended);
                        }
                    }
                } else {
                    record.setAttendedIfChanged(attended);
                }
                return record;
            });
        }
        ctx
            .batchStore(records.values())
            .execute();

        addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        return "redirect:/exercise/{exerciseId}/sheet/{sheetId}/attendance";
    }

    @GetMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/{fileId}/annotations")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    @ResponseBody
    public List<MDAnnotation> getAnnotations(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable int fileId,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        Annotations a = ANNOTATIONS.as("a");
        Uploads u = a.upload().as("u");
        return ctx.select(a.LINE, a.ANNOTATIONOBJ)
            .from(a)
            .where(
                u.EXERCISE.eq(exerciseId),
                u.SHEET.eq(sheetId),
                u.GROUPID.eq(groupId),
                u.TEAMID.eq(teamId),
                u.ASSIGNMENT.eq(assignmentId),
                u.ID.eq(fileId)
            )
            .fetch(r -> {
                int line = r.value1();
                String markdown = r.value2();
                return new MDAnnotation(
                    line,
                    markdown,
                    Markdown.toHtml(markdown)
                );
            });
    }

    @PostMapping("/sheet/{sheetId}/assignment/{assignmentId}/team/{groupId}/{teamId}/{fileId}/annotations")
    @PreAuthorize("#exerciseRoles.canAssess(#groupId)")
    @ResponseBody
    @Transactional
    public Map<String, String> saveAnnotation(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String assignmentId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable int fileId,
        ExerciseRoles exerciseRoles,
        @RequestParam int lineNr,
        @RequestParam String text
    ) {
        metricsService.registerAccess();
        boolean fileExists = ctx
            .selectOne()
            .from(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.ID.eq(fileId)
            )
            .fetchOne() != null;
        if (!fileExists) {
            throw new NotFoundException();
        }

        text = text.trim();
        if (text.isEmpty()) {
            ctx.deleteFrom(ANNOTATIONS).where(ANNOTATIONS.FILEID.eq(fileId), ANNOTATIONS.LINE.eq(lineNr)).execute();
            ctx.deleteFrom(UNREAD).where(UNREAD.FILEID.eq(fileId)).execute();
            return Map.of(
                "status", "deleted",
                "markdown", ""
            );
        } else {
            ctx
                .insertInto(ANNOTATIONS, ANNOTATIONS.FILEID, ANNOTATIONS.LINE, ANNOTATIONS.ANNOTATIONOBJ)
                .values(fileId, lineNr, text)
                .onDuplicateKeyUpdate()
                .set(ANNOTATIONS.ANNOTATIONOBJ, text)
                .execute();
            Students s = STUDENTS.as("s");
            Studentresults sr = STUDENTRESULTS.as("sr");
            ctx
                .insertInto(UNREAD, UNREAD.FILEID, UNREAD.USERID)
                .select(DSL
                    .select(DSL.val(fileId), sr.USERID)
                    .from(sr)
                    .where(
                        sr.EXERCISE.eq(exerciseId),
                        sr.SHEET.eq(sheetId),
                        sr.GROUPID.eq(groupId),
                        sr.TEAMID.eq(teamId)
                    )
                    .union(DSL
                        .select(DSL.val(fileId), s.USERID)
                        .from(s)
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
                )
                .onDuplicateKeyIgnore()
                .execute();
            return Map.of(
                "status", "updated",
                "markdown", Markdown.toHtml(text)
            );
        }
    }

    @GetMapping("/sheet/{sheetId}/zip")
    public void sheetZip(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        ExerciseRoles exerciseRoles,
        HttpServletResponse response
    ) throws IOException {
        metricsService.registerAccess();

        response.setContentType("application/zip");
        response.setStatus(HttpServletResponse.SC_OK);
        String zipFilename = exerciseId + "_" + sheetId + ".zip";
        response.addHeader("Content-Disposition", "attachment; filename=\"" + zipFilename + "\"");

        // groupId -> teamId -> assignmentId -> [files]
        Map<String, Map<String, Map<String, List<Tuple2<LocalDateTime, String>>>>> uploads = new HashMap<>();
        ctx
            .select(UPLOADS.GROUPID, UPLOADS.TEAMID, UPLOADS.ASSIGNMENT, UPLOADS.UPLOAD_DATE, UPLOADS.FILENAME)
            .from(UPLOADS)
            .where(
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                exerciseRoles.applyGroupIdRestriction(UPLOADS.GROUPID),
                UPLOADS.DELETE_DATE.isNull()
            )
            .forEach(r -> uploads
                .computeIfAbsent(r.value1(), groupId -> new HashMap<>())
                .computeIfAbsent(r.value2(), teamId -> new HashMap<>())
                .computeIfAbsent(r.value3(), assignmentId -> new ArrayList<>())
                .add(new Tuple2<>(r.value4(), r.value5()))
            );

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()))) {
            for (Map.Entry<String, Map<String, Map<String, List<Tuple2<LocalDateTime, String>>>>> group : uploads.entrySet()) {
                String groupId = group.getKey();
                String groupFolder = groupId + "/";
                zipOutputStream.putNextEntry(new ZipEntry(groupFolder));
                for (Map.Entry<String, Map<String, List<Tuple2<LocalDateTime, String>>>> team : group.getValue().entrySet()) {
                    String teamId = team.getKey();
                    String teamFolder = groupFolder + teamId + "/";
                    zipOutputStream.putNextEntry(new ZipEntry(teamFolder));
                    for (Map.Entry<String, List<Tuple2<LocalDateTime, String>>> assignment : team.getValue().entrySet()) {
                        String assignmentId = assignment.getKey();
                        String assignmentFolder = teamFolder + assignmentId + "/";
                        zipOutputStream.putNextEntry(new ZipEntry(assignmentFolder));
                        Set<String> existingEntries = new HashSet<>();
                        for (Tuple2<LocalDateTime, String> file : assignment.getValue()) {
                            LocalDateTime uploadDate = file.v1;
                            String filename = file.v2;
                            if (existingEntries.add(filename)) {
                                zipOutputStream.putNextEntry(new ZipEntry(assignmentFolder + filename));
                                Path filePath = uploadManager.getUploadPath(exerciseId, sheetId, assignmentId, groupId, teamId, uploadDate, filename);
                                try (InputStream fileInputStream = Files.newInputStream(filePath)) {
                                    IOUtils.copy(fileInputStream, zipOutputStream);
                                }
                                zipOutputStream.closeEntry();
                            }
                        }
                    }
                }
            }
        }
    }
}
