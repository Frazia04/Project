package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.ExamTaskWithPoints;
import de.rptu.cs.exclaim.data.interfaces.IExam;
import de.rptu.cs.exclaim.data.interfaces.IExamGrade;
import de.rptu.cs.exclaim.data.interfaces.IExamTask;
import de.rptu.cs.exclaim.data.records.ExamParticipantRecord;
import de.rptu.cs.exclaim.data.records.ExamRecord;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.Keys;
import de.rptu.cs.exclaim.schema.tables.Examparticipants;
import de.rptu.cs.exclaim.schema.tables.Examresults;
import de.rptu.cs.exclaim.schema.tables.Exams;
import de.rptu.cs.exclaim.schema.tables.Examtasks;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExerciseRoles;
import de.rptu.cs.exclaim.utils.Comparators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.rptu.cs.exclaim.schema.tables.Examgrades.EXAMGRADES;
import static de.rptu.cs.exclaim.schema.tables.Examparticipants.EXAMPARTICIPANTS;
import static de.rptu.cs.exclaim.schema.tables.Examresults.EXAMRESULTS;
import static de.rptu.cs.exclaim.schema.tables.Exams.EXAMS;
import static de.rptu.cs.exclaim.schema.tables.Examtasks.EXAMTASKS;

@Controller
@RequestMapping("/exercise/{exerciseId}/exam/{examId}")
@RequiredArgsConstructor
@Slf4j
public class ExamController {
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @ModelAttribute
    public ExerciseRoles exerciseRoles(@PathVariable String exerciseId) {
        ExerciseRoles exerciseRoles = accessChecker.getExerciseRoles(exerciseId);
        log.debug("Accessing exercise {} with {}", exerciseId, exerciseRoles);
        return exerciseRoles;
    }

    @GetMapping("/result")
    @PreAuthorize("#exerciseRoles.isStudent")
    public String getExamResult(
        @PathVariable String exerciseId,
        @PathVariable String examId,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();

        // Get the exam, also make sure results are published and the student is registered
        Exams e = EXAMS.as("e");
        Examparticipants p = EXAMPARTICIPANTS.as("p");
        int userId = accessChecker.getUserId();
        IExam exam = ctx
            .select(e)
            .from(e)
            .where(
                e.EXERCISE.eq(exerciseId),
                e.ID.eq(examId),
                DSL.condition(e.SHOW_RESULTS),
                DSL.exists(DSL
                    .selectOne()
                    .from(p)
                    .where(
                        p.EXERCISE.eq(exerciseId),
                        p.EXAMID.eq(examId),
                        p.USERID.eq(userId)
                    )
                )
            )
            .fetchOptional(Record1::value1)
            .orElseThrow(NotFoundException::new);

        // Get tasks with results
        Examtasks t = EXAMTASKS.as("t");
        Examresults r = EXAMRESULTS.as("r");
        List<ExamTaskWithPoints> tasks = ctx
            .select(t.ID, t.MAX_POINTS, r.POINTS)
            .from(t)
            .leftJoin(r).onKey(Keys.FK__EXAMRESULTS__EXAMTASKS).and(r.USERID.eq(userId))
            .where(t.EXERCISE.eq(exerciseId), t.EXAMID.eq(examId))
            .fetch(Records.mapping(ExamTaskWithPoints::new));
        tasks.sort(Comparator.comparing(ExamTaskWithPoints::getTaskId, Comparators.IDENTIFIER));

        // Get grades
        List<? extends IExamGrade> grades = ctx.fetch(EXAMGRADES, EXAMGRADES.EXERCISE.eq(exerciseId), EXAMGRADES.EXAMID.eq(examId));
        grades.sort(Comparator.comparing(IExamGrade::getMinPoints).reversed());

        // Compute overall result
        BigDecimal maxPoints = BigDecimal.ZERO;
        BigDecimal sumPoints = null;
        for (ExamTaskWithPoints task : tasks) {
            maxPoints = maxPoints.add(task.getMaxPoints());
            BigDecimal points = task.getPoints();
            if (points != null) {
                sumPoints = sumPoints == null ? points : sumPoints.add(points);
            }
        }
        String grade = null;
        if (sumPoints != null) {
            for (IExamGrade g : grades) {
                if (g.getMinPoints().compareTo(sumPoints) <= 0) {
                    grade = g.getGrade();
                    break;
                }
            }
        }

        model.addAttribute("exam", exam);
        model.addAttribute("tasks", tasks);
        model.addAttribute("grades", grades);
        model.addAttribute("sumPoints", sumPoints);
        model.addAttribute("maxPoints", maxPoints);
        model.addAttribute("grade", grade);
        return "exam/result";
    }

    @GetMapping("/evaluation")
    @PreAuthorize("#exerciseRoles.canAssess")
    public String getExamEvaluation(
        @PathVariable String exerciseId,
        @PathVariable String examId,
        Model model,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();

        // Get the exam, also make sure results are published when accessed by a tutor
        IExam exam = ctx
            .selectFrom(EXAMS)
            .where(
                EXAMS.EXERCISE.eq(exerciseId),
                EXAMS.ID.eq(examId),
                exerciseRoles.getIsAssistant() ? DSL.noCondition() : DSL.condition(EXAMS.SHOW_RESULTS)
            )
            .fetchOptional()
            .orElseThrow(NotFoundException::new);

        // Get tasks with results
        List<? extends IExamTask> tasks = ctx.fetch(EXAMTASKS, EXAMTASKS.EXERCISE.eq(exerciseId), EXAMTASKS.EXAMID.eq(examId));
        tasks.sort(Comparator.comparing(IExamTask::getExamTaskId, Comparators.IDENTIFIER));

        // Get grades
        List<? extends IExamGrade> grades = ctx.fetch(EXAMGRADES, EXAMGRADES.EXERCISE.eq(exerciseId), EXAMGRADES.EXAMID.eq(examId));
        grades.sort(Comparator.comparing(IExamGrade::getMinPoints).reversed());

        model.addAttribute("exam", exam);
        model.addAttribute("tasks", tasks);
        model.addAttribute("grades", grades);

        return "exam/evaluation";
    }

    @GetMapping("/gradeoverview")
    @ResponseBody
    public Map<String, Integer> getExamGradeOverview(
        @PathVariable String exerciseId,
        @PathVariable String examId,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();

        // Assistants can always see the results, tutors and students only when published
        if (!exerciseRoles.getIsAssistant()) {
            Exams e = EXAMS.as("e");
            Examparticipants p = EXAMPARTICIPANTS.as("p");
            if (ctx
                .selectOne()
                .from(e)
                .where(
                    e.EXERCISE.eq(exerciseId),
                    e.ID.eq(examId),
                    DSL.condition(e.SHOW_RESULTS),
                    // tutor, or a student that is registered to that exam
                    exerciseRoles.canAssess() ? DSL.noCondition() : DSL.exists(DSL
                        .selectOne()
                        .from(p)
                        .where(
                            p.EXERCISE.eq(exerciseId),
                            p.EXAMID.eq(examId),
                            p.USERID.eq(accessChecker.getUserId())
                        )
                    )
                )
                .fetchOne(Record1::value1) == null
            ) {
                throw new NotFoundException();
            }
        }

        // Get grades
        List<? extends IExamGrade> grades = ctx.fetch(EXAMGRADES, EXAMGRADES.EXERCISE.eq(exerciseId), EXAMGRADES.EXAMID.eq(examId));
        grades.sort(Comparator.comparing(IExamGrade::getMinPoints).reversed());

        // Get points
        List<BigDecimal> pointsOccurrences = ctx
            .select(DSL.sum(EXAMRESULTS.POINTS).as("sumPoints"))
            .from(EXAMRESULTS)
            .where(EXAMRESULTS.EXERCISE.eq(exerciseId), EXAMRESULTS.EXAMID.eq(examId))
            .groupBy(EXAMRESULTS.EXERCISE, EXAMRESULTS.EXAMID, EXAMRESULTS.USERID)
            .fetch(Record1::value1);

        // Count grades
        Map<String, Integer> gradeCounts = new LinkedHashMap<>();
        grades.forEach(gr -> gradeCounts.put(gr.getGrade(), 0));
        for (BigDecimal points : pointsOccurrences) {
            for (IExamGrade gr : grades) {
                if (gr.getMinPoints().compareTo(points) <= 0) {
                    gradeCounts.merge(gr.getGrade(), 1, Integer::sum);
                    break;
                }
            }
        }
        return gradeCounts;
    }

    @PostMapping("/register")
    public String register(
        @PathVariable String exerciseId,
        @PathVariable String examId,
        ExerciseRoles exerciseRoles
    ) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        ExamRecord exam = ctx.fetchOptional(EXAMS, EXAMS.EXERCISE.eq(exerciseId), EXAMS.ID.eq(examId)).orElseThrow(NotFoundException::new);
        if (exam.getRegistrationOpen() && exerciseRoles.isStudent()) {
            ExamParticipantRecord participant = ctx.fetchOne(
                EXAMPARTICIPANTS,
                EXAMPARTICIPANTS.EXERCISE.eq(exerciseId),
                EXAMPARTICIPANTS.EXAMID.eq(examId),
                EXAMPARTICIPANTS.USERID.eq(userId)
            );
            if (participant == null) {
                participant = ctx.newRecord(EXAMPARTICIPANTS);
                participant.setExerciseId(exerciseId);
                participant.setExamId(examId);
                participant.setUserId(accessChecker.getUserId());
                participant.store();
            } else {
                participant.delete();
            }
        }
        return "redirect:/exercise/{exerciseId}";
    }
}
