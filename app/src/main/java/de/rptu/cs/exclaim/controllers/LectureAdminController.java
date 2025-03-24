package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.AssistantRecord;
import de.rptu.cs.exclaim.data.records.ExerciseRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.enums.GroupJoin;
import de.rptu.cs.exclaim.schema.enums.Term;
import de.rptu.cs.exclaim.schema.tables.Assistants;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.utils.Comparators;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Record1;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXERCISE_ID_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXERCISE_LECTURE_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.EXERCISE_TERM_COMMENT_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.ID_REGEX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Exercises.EXERCISES;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

@Controller
@PreAuthorize("@accessChecker.isAdmin()")
@Slf4j
@RequiredArgsConstructor
public class LectureAdminController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @Value
    public static class CreateLectureForm {
        @NotBlank @Size(max = EXERCISE_ID_LENGTH_MAX) @Pattern(regexp = ID_REGEX) String id;
        @NotBlank @Size(max = EXERCISE_LECTURE_LENGTH_MAX) String lecture;
        @Nullable String year;
        @Nullable Term term;
        @Nullable @Size(max = EXERCISE_TERM_COMMENT_LENGTH_MAX) String termComment;
    }

    @Value
    public static class EditLectureForm {
        @NotBlank @Size(max = EXERCISE_LECTURE_LENGTH_MAX) String lecture;
        @Nullable String year;
        @Nullable Term term;
        @Nullable @Size(max = EXERCISE_TERM_COMMENT_LENGTH_MAX) String termComment;
    }

    @GetMapping("/lectures")
    public String getLectureAdminPage(Model model) {
        metricsService.registerAccess();
        List<ExerciseRecord> exercises = ctx.fetch(EXERCISES);
        exercises.sort(Comparators.EXERCISE_BY_TERM);
        model.addAttribute("exercises", exercises);
        return "lecture/admin";
    }

    @GetMapping("/lectures/create")
    public String getCreateLecturePage(Model model) {
        metricsService.registerAccess();
        model.addAttribute(new CreateLectureForm("", "", "", null, ""));
        return "lecture/create";
    }

    @PostMapping("/lectures/create")
    @Transactional
    public String create(@Valid CreateLectureForm createLectureForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        short year = 0;
        if (StringUtils.isNotEmpty(createLectureForm.year)) {
            try {
                year = Short.parseShort(createLectureForm.year);
            } catch (NumberFormatException e) {
                bindingResult.rejectValue("year", "Invalid");
            }
        }
        if (!bindingResult.hasErrors()) {
            ExerciseRecord exerciseRecord = ctx.newRecord(EXERCISES);
            exerciseRecord.setExerciseId(createLectureForm.id);
            exerciseRecord.setLecture(createLectureForm.lecture);
            exerciseRecord.setYear(year);
            exerciseRecord.setTerm(createLectureForm.term);
            exerciseRecord.setTermComment(StringUtils.defaultString(createLectureForm.termComment));
            exerciseRecord.setGroupJoin(GroupJoin.NONE);
            try {
                exerciseRecord.insert();
                log.info("Lecture {} has been created by admin {}", exerciseRecord, accessChecker.getUser());
                addRedirectMessage(ControllerUtils.MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                return "redirect:/lectures/" + createLectureForm.id + "/assistants";
            } catch (DuplicateKeyException e) {
                bindingResult.rejectValue("id", "Unique");
            }
        }
        return "/lecture/create";
    }

    @GetMapping("/lectures/{exerciseId}/edit")
    public String getEditLecturePage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        ExerciseRecord exerciseRecord = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        model.addAttribute("exercise", exerciseRecord);
        short year = exerciseRecord.getYear();
        model.addAttribute(new EditLectureForm(
            exerciseRecord.getLecture(),
            year == 0 ? "" : String.valueOf(year),
            exerciseRecord.getTerm(),
            StringUtils.defaultString(exerciseRecord.getTermComment())
        ));
        return "lecture/edit";
    }

    @PostMapping("/lectures/{exerciseId}/edit")
    @Transactional
    public String edit(@PathVariable String exerciseId, @Valid EditLectureForm editLectureForm, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExerciseRecord exerciseRecord = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        short year = 0;
        if (StringUtils.isNotEmpty(editLectureForm.year)) {
            try {
                year = Short.parseShort(editLectureForm.year);
            } catch (NumberFormatException e) {
                bindingResult.rejectValue("year", "Invalid");
            }
        }
        if (!bindingResult.hasErrors()) {
            exerciseRecord.setLectureIfChanged(editLectureForm.lecture);
            exerciseRecord.setYearIfChanged(year);
            exerciseRecord.setTermIfChanged(editLectureForm.term);
            exerciseRecord.setTermCommentIfChanged(StringUtils.defaultString(editLectureForm.termComment));
            if (exerciseRecord.changed()) {
                exerciseRecord.update();
                log.info("Lecture data for {} has been changed by admin {}", exerciseRecord.getExerciseId(), accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            }
            return "redirect:/lectures";
        }
        model.addAttribute("exercise", exerciseRecord);
        return "/lecture/edit";
    }

    @PostMapping("/lectures/{exerciseId}/delete")
    @Transactional
    public String delete(@PathVariable String exerciseId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        try {
            if (ctx
                .deleteFrom(EXERCISES)
                .where(EXERCISES.ID.eq(exerciseId))
                .execute() == 1
            ) {
                log.info("Lecture {} has been deleted by admin {}", exerciseId, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("lecture-admin.delete-lecture-success", new Object[]{exerciseId}), redirectAttributes);
            }
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("lecture-admin.delete-lecture-failed", new Object[]{exerciseId}) + "\n" + e, redirectAttributes);
        }
        return "redirect:/lectures";
    }

    @GetMapping("/lectures/{exerciseId}/assistants")
    public String getAssistantsPage(@PathVariable String exerciseId, Model model) {
        metricsService.registerAccess();
        Assistants a = ASSISTANTS.as("a");
        Users u = a.user().as("u");
        List<IUser> assistants = ctx
            .select(u)
            .from(a)
            .where(a.EXERCISEID.eq(exerciseId))
            .fetch(Record1::value1);
        assistants.sort(Comparators.USER_BY_NAME);
        model.addAttribute("assistants", assistants);
        return "lecture/assistants";
    }

    @PostMapping("/lectures/{exerciseId}/assistants")
    @Transactional
    public String addAssistant(@PathVariable String exerciseId, @RequestParam String username, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        ExerciseRecord exerciseRecord = ctx.fetchOptional(EXERCISES, EXERCISES.ID.eq(exerciseId)).orElseThrow(NotFoundException::new);
        Integer assistantUserId = ctx
            .select(USERS.USERID)
            .from(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOne(Record1::value1);
        if (assistantUserId != null) {
            AssistantRecord assistant = ctx.newRecord(ASSISTANTS);
            assistant.setExerciseId(exerciseRecord.getExerciseId());
            assistant.setUserId(assistantUserId);
            try {
                assistant.insert();
                log.info("Assistant {} has been added by admin {}", assistant, accessChecker.getUser());
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            } catch (DuplicateKeyException e) {
                // ignore duplicate assistant role
            }
        } else {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("common.username-not-found"), redirectAttributes);
        }
        return "redirect:/lectures/{exerciseId}/assistants";
    }

    @PostMapping("/lectures/{exerciseId}/assistants/{userId}/delete")
    @Transactional
    public String deleteAssistant(@PathVariable String exerciseId, @PathVariable int userId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (ctx
            .deleteFrom(ASSISTANTS)
            .where(ASSISTANTS.USERID.eq(userId), ASSISTANTS.EXERCISEID.eq(exerciseId))
            .execute() == 1
        ) {
            log.info("Assistant user id {} for lecture {} has been deleted by admin {}", userId, exerciseId, accessChecker.getUser());
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }
        return "redirect:/lectures/{exerciseId}/assistants";
    }
}
