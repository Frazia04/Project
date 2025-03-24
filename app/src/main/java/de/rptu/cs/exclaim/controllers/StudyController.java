package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.records.ExerciseRecord;
import de.rptu.cs.exclaim.data.records.StudyStatisticRecord;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.security.AccessChecker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jooq.DSLContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static de.rptu.cs.exclaim.Study.STUDY_EXERCISE;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Grouppreferences.GROUPPREFERENCES;
import static de.rptu.cs.exclaim.schema.tables.StudyStatistics.STUDY_STATISTICS;

@Controller
@RequiredArgsConstructor
public class StudyController {
    private final DSLContext ctx;
    private final AccessChecker accessChecker;

    @Value
    public static class StudentInfoForm {
        String courseOfStudies;
        String semester;
    }

    @GetMapping("/study/student-info")
    public String getStudentInfoPage(Model model) {
        if (!model.containsAttribute("studentInfoForm")) {
            StudyStatisticRecord studyStatisticRecord = ctx.fetchOne(STUDY_STATISTICS, STUDY_STATISTICS.USERID.eq(accessChecker.getUserId()));
            model.addAttribute(studyStatisticRecord == null
                ? new StudentInfoForm("", "")
                : new StudentInfoForm(studyStatisticRecord.getCourseOfStudies(), studyStatisticRecord.getSemester())
            );
        }
        return "study/student-info";
    }

    @PostMapping("/study/student-info")
    public String saveStudentInfo(@Valid StudentInfoForm studentInfoForm) {
        int userId = accessChecker.getUserId();
        ExerciseRecord exercise = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(STUDY_EXERCISE)).orElseThrow(NotFoundException::new);

        StudyStatisticRecord studyStatisticRecord = ctx
            .selectFrom(STUDY_STATISTICS)
            .where(STUDY_STATISTICS.USERID.eq(userId))
            .forUpdate()
            .fetchOne();
        if (studyStatisticRecord == null) {
            studyStatisticRecord = ctx.newRecord(STUDY_STATISTICS);
            studyStatisticRecord.setUserId(userId);
        }
        studyStatisticRecord.setCourseOfStudiesIfChanged(studentInfoForm.courseOfStudies);
        studyStatisticRecord.setSemesterIfChanged(studentInfoForm.semester);
        if (studyStatisticRecord.changed()) {
            studyStatisticRecord.store();
        }

        if (exercise.getGroupJoin() == GroupJoin.PREFERENCES) {
            boolean hasPreferences = ctx
                .selectOne()
                .from(GROUPPREFERENCES)
                .where(
                    GROUPPREFERENCES.EXERCISEID.eq(STUDY_EXERCISE),
                    GROUPPREFERENCES.USERID.eq(userId)
                )
                .fetchOne() != null;

            if (!hasPreferences) {
                return "redirect:/exercise/" + STUDY_EXERCISE + "/groups/preferences";
            }
        }

        return "redirect:/exercise/" + STUDY_EXERCISE;
    }
}
