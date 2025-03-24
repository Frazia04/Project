package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import de.rptu.cs.exclaim.data.ExerciseWithRegistered;
import de.rptu.cs.exclaim.data.records.ExerciseRecord;
import de.rptu.cs.exclaim.data.records.StudentRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.tables.Exercises;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.utils.Comparators;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static de.rptu.cs.exclaim.Study.STUDY_EXERCISE;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;

@Controller
@RequiredArgsConstructor
public class LectureJoinController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @GetMapping("/join")
    public String getLectureJoinPage(Model model) {
        metricsService.registerAccess();
        Exercises e = EXERCISES.as("e");
        Students s = STUDENTS.as("s");
        List<ExerciseWithRegistered> exercises = ctx
            .select(
                e,
                DSL.field(s.USERID.isNotNull()).as("registered")
            )
            .from(e)
            .leftJoin(s).onKey(Keys.FK__STUDENTS__EXERCISES).and(s.USERID.eq(accessChecker.getUserId()))
            .where(e.REGISTRATION_OPEN)
            .fetch(Records.mapping(ExerciseWithRegistered::new));
        exercises.sort(Comparators.EXERCISE_BY_TERM);
        model.addAttribute("exercises", exercises);
        return "lecture/join";
    }

    @PostMapping("/join/{exerciseId}")
    @Transactional
    public String join(@PathVariable String exerciseId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExerciseRecord exercise = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        if (!exercise.getRegistrationOpen()) {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("lecture-join.cannot-join"), redirectAttributes);
            return "redirect:/join";
        }

        StudentRecord studentRecord = ctx.newRecord(STUDENTS);
        studentRecord.setExerciseId(exerciseId);
        studentRecord.setUserId(accessChecker.getUserId());
        try {
            studentRecord.insert();
        } catch (DuplicateKeyException e) {
            // already joined, ignore exception
        }
        if (STUDY_EXERCISE.equals(exerciseId)) {
            return "redirect:/study/student-info";
        }
        return switch (exercise.getGroupJoin()) {
            case GROUP -> "redirect:/exercise/{exerciseId}/groups";
            case PREFERENCES -> "redirect:/exercise/{exerciseId}/groups/preferences";
            default -> "redirect:/exercise/{exerciseId}";
        };
    }

    @PostMapping("/leave/{exerciseId}")
    @Transactional
    public String leave(@PathVariable String exerciseId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExerciseRecord exercise = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        if (!exercise.getRegistrationOpen()) {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("lecture-join.cannot-leave"), redirectAttributes);
        } else {
            try {
                ctx
                    .deleteFrom(STUDENTS)
                    .where(STUDENTS.USERID.eq(accessChecker.getUserId()), STUDENTS.EXERCISEID.eq(exerciseId))
                    .execute();
            } catch (DataIntegrityViolationException e) {
                addRedirectMessage(MessageType.ERROR, msg.getMessage("lecture-join.cannot-leave"), redirectAttributes);
            }
        }
        return "redirect:/join";
    }
}
