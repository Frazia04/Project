package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.records.ExerciseRecord;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.utils.Comparators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;

/**
 * Display the home page (login page for guests, list of exercises for logged-in users).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @GetMapping("/")
    public String getHome(Model model) {
        metricsService.registerAccess();
        int userId = accessChecker.getUserId();
        List<ExerciseRecord> exercises = ctx.fetch(EXERCISES, EXERCISES.ID.in(DSL
            .select(STUDENTS.EXERCISEID).from(STUDENTS).where(STUDENTS.USERID.eq(userId))
            .union(DSL.select(TUTORS.EXERCISEID).from(TUTORS).where(TUTORS.USERID.eq(userId)))
            .union(DSL.select(ASSISTANTS.EXERCISEID).from(ASSISTANTS).where(ASSISTANTS.USERID.eq(userId)))
        ));
        exercises.sort(Comparators.EXERCISE_BY_TERM);
        model.addAttribute("exercises", exercises);
        return "home";
    }
}
