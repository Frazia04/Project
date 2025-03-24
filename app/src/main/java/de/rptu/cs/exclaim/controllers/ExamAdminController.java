package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.interfaces.IExam;
import de.rptu.cs.exclaim.data.interfaces.IExamGrade;
import de.rptu.cs.exclaim.data.interfaces.IExamTask;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.ExamGradeRecord;
import de.rptu.cs.exclaim.data.records.ExamRecord;
import de.rptu.cs.exclaim.data.records.ExamTaskRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.tables.Examparticipants;
import de.rptu.cs.exclaim.schema.tables.Examresults;
import de.rptu.cs.exclaim.schema.tables.Examtasks;
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
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXAM_GRADE_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXAM_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXAM_LABEL_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXAM_LOCATION_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXAM_TASK_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.ID_REGEX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Examgrades.EXAMGRADES;
import static de.rptu.cs.exclaim.schema.tables.Examparticipants.EXAMPARTICIPANTS;
import static de.rptu.cs.exclaim.schema.tables.Examresults.EXAMRESULTS;
import static de.rptu.cs.exclaim.schema.tables.Exams.EXAMS;
import static de.rptu.cs.exclaim.schema.tables.Examtasks.EXAMTASKS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Controller
@RequestMapping("/exercise/{exerciseId}")
@PreAuthorize("@accessChecker.isAssistantFor(#exerciseId)")
@RequiredArgsConstructor
@Slf4j
public class ExamAdminController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @Value
    public static class CreateExamForm {
        public CreateExamForm(String examId, String label, String date, String location, @Nullable Boolean registrationOpen) {
            this.examId = examId;
            this.label = label;
            this.date = date;
            this.location = location;
            this.registrationOpen = registrationOpen != null && registrationOpen;
        }

        @NotBlank @Size(max = EXAM_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String examId;
        @NotBlank @Size(max = EXAM_LABEL_LENGTH_MAX) String label;
        @NotBlank String date;
        @NotBlank @Size(max = EXAM_LOCATION_LENGTH_MAX) String location;
        boolean registrationOpen;
    }

    @Value
    public static class EditExamForm {
        public EditExamForm(String label, String date, String location, @Nullable Boolean registrationOpen, @Nullable Boolean showResults) {
            this.label = label;
            this.date = date;
            this.location = location;
            this.registrationOpen = registrationOpen != null && registrationOpen;
            this.showResults = showResults != null && showResults;
        }

        @NotBlank @Size(max = EXAM_LABEL_LENGTH_MAX) String label;
        @NotBlank String date;
        @NotBlank @Size(max = EXAM_LOCATION_LENGTH_MAX) String location;
        boolean registrationOpen;
        boolean showResults;
    }

    @Value
    public static class CreateExamTaskForm {
        @NotBlank @Size(max = EXAM_TASK_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String examTaskId;
        @Nullable String maxPoints;
    }

    @Value
    public static class EditExamTaskForm {
        @Nullable String maxPoints;
    }

    @Value
    public static class CreateExamGradeForm {
        @NotBlank @Size(max = EXAM_GRADE_LENGTH_MAX) String grade;
        @Nullable String minPoints;
    }

    @GetMapping("/exams")
    public String getExams(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        List<? extends IExam> exams = ctx.fetch(EXAMS, EXAMS.EXERCISE.eq(exerciseId));
        model.addAttribute("exams", exams);
        return "exam/exams-admin";
    }

    @GetMapping("/exams/create")
    public String getCreateExamPage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateExamForm("", "", "", "", false));
        return "exam/create-exam";
    }

    @PostMapping("/exams/create")
    public String createExam(@PathVariable String exerciseId, @Valid CreateExamForm createExamForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        LocalDateTime date = null;
        try {
            date = LocalDateTime.parse(createExamForm.date);
        } catch (DateTimeParseException e) {
            bindingResult.rejectValue("date", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            ExamRecord examRecord = ctx.newRecord(EXAMS);
            examRecord.setExerciseId(exerciseId);
            examRecord.setExamId(createExamForm.examId);
            examRecord.setLabel(createExamForm.label);
            examRecord.setDate(Objects.requireNonNull(date));
            examRecord.setLocation(createExamForm.location);
            examRecord.setRegistrationOpen(createExamForm.registrationOpen);
            examRecord.setShowResults(false);
            try {
                examRecord.insert();
                log.info("Exam {} has been created by assistant {}", examRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/exam/" + examRecord.getExamId() + "/edit";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("examId", "Unique");
            }
        }
        return "exam/create-exam";
    }

    @GetMapping("/exam/{examId}/edit")
    public String getEditExamPage(@PathVariable String exerciseId, @PathVariable String examId, Model model) {
        metricsService.registerAccess();
        ExamRecord exam = ctx
            .fetchOptional(EXAMS, EXAMS.EXERCISE.eq(exerciseId), EXAMS.ID.eq(examId))
            .orElseThrow(NotFoundException::new);
        model.addAttribute(new EditExamForm(exam.getLabel(), exam.getDate().toString(), exam.getLocation(), exam.getRegistrationOpen(), exam.getShowResults()));
        model.addAttribute("tasks", ctx.fetch(EXAMTASKS, EXAMTASKS.EXERCISE.eq(exerciseId), EXAMTASKS.EXAMID.eq(examId)));
        return "exam/edit-exam";
    }

    @PostMapping("/exam/{examId}/edit")
    public String editExam(@PathVariable String exerciseId, @PathVariable String examId, @Valid EditExamForm editExamForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExamRecord examRecord = ctx
            .fetchOptional(EXAMS, EXAMS.EXERCISE.eq(exerciseId), EXAMS.ID.eq(examId))
            .orElseThrow(NotFoundException::new);
        LocalDateTime date = null;
        try {
            date = LocalDateTime.parse(editExamForm.date);
        } catch (DateTimeParseException e) {
            bindingResult.rejectValue("date", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            examRecord.setLabelIfChanged(editExamForm.label);
            examRecord.setDateIfChanged(Objects.requireNonNull(date));
            examRecord.setLocationIfChanged(editExamForm.location);
            examRecord.setRegistrationOpenIfChanged(editExamForm.registrationOpen);
            examRecord.setShowResultsIfChanged(editExamForm.showResults);
            if (examRecord.changed()) {
                examRecord.update();
                log.info("Exam {} has been changed by assistant {}", examRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/exam/{examId}/edit";
        }
        return "exam/edit-exam";
    }

    @PostMapping("/exam/{examId}/delete")
    public String deleteExam(@PathVariable String exerciseId, @PathVariable String examId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        try {
            if (ctx
                .deleteFrom(EXAMS)
                .where(EXAMS.EXERCISE.eq(exerciseId), EXAMS.ID.eq(examId))
                .execute() == 1
            ) {
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, "Klausur " + examId + " wurde gelöscht.", redirectAttributes);
            } else {
                addRedirectMessage(ControllerUtils.MessageType.ERROR, "Klausur " + examId + " konnte nicht gelöscht werden.", redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(ControllerUtils.MessageType.ERROR, "Klausur " + examId + " konnte aufgrund von vorhandenen Daten nicht gelöscht werden.\n" + e, redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/exams";
    }

    @GetMapping("/exam/{examId}/create-task")
    public String getCreateExamTaskPage(@PathVariable String exerciseId, @PathVariable String examId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateExamTaskForm("", ""));
        return "exam/create-task";
    }

    @PostMapping("/exam/{examId}/create-task")
    public String createExamTask(@PathVariable String exerciseId, @PathVariable String examId, @Valid CreateExamTaskForm createExamTaskForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        BigDecimal maxPoints = BigDecimal.ZERO;
        try {
            maxPoints = parsePoints(createExamTaskForm.maxPoints);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxPoints", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            ExamTaskRecord examTaskRecord = ctx.newRecord(EXAMTASKS);
            examTaskRecord.setExerciseId(exerciseId);
            examTaskRecord.setExamId(examId);
            examTaskRecord.setExamTaskId(createExamTaskForm.examTaskId);
            examTaskRecord.setMaxPoints(maxPoints);
            try {
                examTaskRecord.insert();
                log.info("Exam task {} has been created by assistant {}", examTaskRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/exam/{examId}/edit";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("examTaskId", "Unique");
            }
        }
        return "exam/create-task";
    }

    @GetMapping("/exam/{examId}/task/{examTaskId}/edit")
    public String getEditExamTaskPage(@PathVariable String exerciseId, @PathVariable String examId, @PathVariable String examTaskId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(ctx
            .select(EXAMTASKS.MAX_POINTS)
            .from(EXAMTASKS)
            .where(
                EXAMTASKS.EXERCISE.eq(exerciseId),
                EXAMTASKS.EXAMID.eq(examId),
                EXAMTASKS.ID.eq(examTaskId)
            )
            .fetchOptional(r -> new EditExamTaskForm(r.value1().toString()))
            .orElseThrow(NotFoundException::new)
        );
        return "exam/edit-task";
    }

    @PostMapping("/exam/{examId}/task/{examTaskId}/edit")
    public String editExamTask(@PathVariable String exerciseId, @PathVariable String examId, @PathVariable String examTaskId, @Valid EditExamTaskForm editExamTaskForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        BigDecimal maxPoints = BigDecimal.ZERO;
        try {
            maxPoints = parsePoints(editExamTaskForm.maxPoints);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("maxPoints", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            ExamTaskRecord examTaskRecord = ctx
                .fetchOptional(
                    EXAMTASKS,
                    EXAMTASKS.EXERCISE.eq(exerciseId),
                    EXAMTASKS.EXAMID.eq(examId),
                    EXAMTASKS.ID.eq(examTaskId)
                )
                .orElseThrow(NotFoundException::new);
            examTaskRecord.setMaxPointsIfChanged(maxPoints);
            if (examTaskRecord.changed()) {
                examTaskRecord.update();
                log.info("Exam task {} has been changed by assistant {}", examTaskRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/exercise/{exerciseId}/exam/{examId}/edit";
        }
        return "exam/edit-task";
    }

    @PostMapping("/exam/{examId}/task/{examTaskId}/delete")
    public String deleteExamTask(@PathVariable String exerciseId, @PathVariable String examId, @PathVariable String examTaskId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        try {
            if (ctx
                .deleteFrom(EXAMTASKS)
                .where(
                    EXAMTASKS.EXERCISE.eq(exerciseId),
                    EXAMTASKS.EXAMID.eq(examId),
                    EXAMTASKS.ID.eq(examTaskId)
                )
                .execute() == 1
            ) {
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, "Aufgabe " + examTaskId + " wurde gelöscht.", redirectAttributes);
            } else {
                addRedirectMessage(ControllerUtils.MessageType.ERROR, "Aufgabe " + examTaskId + " konnte nicht gelöscht werden.", redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(ControllerUtils.MessageType.ERROR, "Aufgabe " + examTaskId + " konnte aufgrund von vorhandenen Daten nicht gelöscht werden.\n" + e, redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/exam/{examId}/edit";
    }

    @GetMapping("/exam/{examId}/grades")
    public String getGradesPage(@PathVariable String exerciseId, @PathVariable String examId, Model model) {
        metricsService.registerAccess();
        List<? extends IExamGrade> grades = ctx.fetch(EXAMGRADES, EXAMGRADES.EXERCISE.eq(exerciseId), EXAMGRADES.EXAMID.eq(examId));
        grades.sort(Comparator.comparing(IExamGrade::getMinPoints).reversed());
        model.addAttribute("grades", grades);
        return "exam/grades";
    }

    @GetMapping("/exam/{examId}/create-grade")
    public String getCreateExamGradePage(@PathVariable String exerciseId, @PathVariable String examId, Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateExamGradeForm("", ""));
        return "exam/create-grade";
    }

    @PostMapping("/exam/{examId}/create-grade")
    public String createExamGrade(@PathVariable String exerciseId, @PathVariable String examId, @Valid CreateExamGradeForm createExamGradeForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        BigDecimal minPoints = BigDecimal.ZERO;
        try {
            minPoints = parsePoints(createExamGradeForm.minPoints);
        } catch (NumberFormatException e) {
            bindingResult.rejectValue("minPoints", "Invalid");
        }
        if (!bindingResult.hasErrors()) {
            ExamGradeRecord examGradeRecord = ctx.newRecord(EXAMGRADES);
            examGradeRecord.setExerciseId(exerciseId);
            examGradeRecord.setExamId(examId);
            examGradeRecord.setGrade(createExamGradeForm.grade);
            examGradeRecord.setMinPoints(minPoints);
            try {
                examGradeRecord.insert();
                log.info("Exam grade {} has been created by assistant {}", examGradeRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/exercise/{exerciseId}/exam/{examId}/grades";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("grade", "Unique");
            }
        }
        return "exam/create-grade";
    }

    @PostMapping("/exam/{examId}/grade/{grade}/delete")
    public String deleteExamGrade(@PathVariable String exerciseId, @PathVariable String examId, @PathVariable String grade, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (ctx
            .deleteFrom(EXAMGRADES)
            .where(
                EXAMGRADES.EXERCISE.eq(exerciseId),
                EXAMGRADES.EXAMID.eq(examId),
                EXAMGRADES.GRADE.eq(grade)
            )
            .execute() == 1
        ) {
            addRedirectMessage(ControllerUtils.MessageType.SUCCESS, "Note " + grade + " wurde gelöscht.", redirectAttributes);
        } else {
            addRedirectMessage(ControllerUtils.MessageType.ERROR, "Note " + grade + " konnte nicht gelöscht werden.", redirectAttributes);
        }
        return "redirect:/exercise/{exerciseId}/exam/{examId}/grades";
    }

    @GetMapping("/exam/{examId}/participants")
    public String getExamParticipants(@PathVariable String exerciseId, @PathVariable String examId, Model model) {
        metricsService.registerAccess();

        Examparticipants p = EXAMPARTICIPANTS.as("p");
        Users u = USERS.as("u");
        Examtasks t = EXAMTASKS.as("t");
        Examresults r = EXAMRESULTS.as("r");

        List<? extends IExamTask> tasks = ctx.fetch(EXAMTASKS, EXAMTASKS.EXERCISE.eq(exerciseId), EXAMTASKS.EXAMID.eq(examId));
        tasks.sort(Comparator.comparing(IExamTask::getExamTaskId, Comparators.IDENTIFIER));

        List<? extends IExamGrade> grades = ctx.fetch(EXAMGRADES, EXAMGRADES.EXERCISE.eq(exerciseId), EXAMGRADES.EXAMID.eq(examId));
        grades.sort(Comparator.comparing(IExamGrade::getMinPoints).reversed());

        Object[] data = ctx
            .select(
                u,
                DSL.multisetAgg(t.ID, r.POINTS).convertFrom(rs -> rs.intoMap(Record2::value1, Record2::value2))
            )
            .from(p)
            .innerJoin(u).on(u.USERID.eq(p.USERID))
            .leftJoin(t).on(t.EXERCISE.eq(exerciseId), t.EXAMID.eq(examId))
            .leftJoin(r).onKey(Keys.FK__EXAMRESULTS__EXAMPARTICIPANTS).and(r.TASKID.eq(t.ID))
            .where(
                p.EXERCISE.eq(exerciseId),
                p.EXAMID.eq(examId)
            )
            .groupBy(u)
            .fetch(rr -> {
                IUser user = rr.value1();
                Map<String, BigDecimal> pointsPerTask = rr.value2();
                BigDecimal sumPoints = null;
                for (BigDecimal points : pointsPerTask.values()) {
                    if (points != null) {
                        sumPoints = sumPoints == null ? points : sumPoints.add(points);
                    }
                }
                String grade = null;
                if (sumPoints != null) {
                    for (IExamGrade gr : grades) {
                        if (gr.getMinPoints().compareTo(sumPoints) <= 0) {
                            grade = gr.getGrade();
                            break;
                        }
                    }
                }
                return new Object[]{
                    user.getUserId(),
                    user.getStudentId(),
                    user.getFirstname(),
                    user.getLastname(),
                    user.getEmail(),
                    tasks.stream().map(task -> pointsPerTask.get(task.getExamTaskId())).toArray(BigDecimal[]::new),
                    grade,
                };
            })
            .toArray();

        model.addAttribute("tasks", tasks);
        model.addAttribute("maxPointsTotal", tasks.stream().map(IExamTask::getMaxPoints).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("data", JsonUtils.toJson(data));
        return "exam/participants";
    }

    private static BigDecimal parsePoints(@Nullable String maxPoints) throws NumberFormatException {
        if (StringUtils.isNotEmpty(maxPoints)) {
            BigDecimal result = new BigDecimal(maxPoints);
            if (result.signum() < 0) {
                throw new NumberFormatException("points cannot be negative");
            }
            return result;
        }
        return BigDecimal.ZERO;
    }
}
