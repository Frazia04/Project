package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.tables.Assistants;
import de.rptu.cs.exclaim.schema.tables.Students;
import de.rptu.cs.exclaim.schema.tables.Tutors;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExclaimAuthentication;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import de.rptu.cs.exclaim.security.ExclaimUserPrincipal;
import de.rptu.cs.exclaim.utils.Comparators;
import de.rptu.cs.exclaim.utils.JsonUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.EMAIL_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.FIRSTNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.LASTNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.PASSWORD_LENGTH_MIN;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_LENGTH_MIN;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_REGEX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.redirectBindingResult;
import static de.rptu.cs.exclaim.schema.tables.Assistants.ASSISTANTS;
import static de.rptu.cs.exclaim.schema.tables.Students.STUDENTS;
import static de.rptu.cs.exclaim.schema.tables.Tutors.TUTORS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;

/**
 * Allow admins to view, edit and delete all users. Allow assistants to view some data for all users.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class UserAdminController {
    private final ExclaimProperties exclaimProperties;
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;
    private final ExclaimPasswordEncoder pe;

    @Value
    public static class EditUserForm {
        public EditUserForm(String username, String firstname, String lastname, @Nullable String studentId, String email, @Nullable String language, @Nullable Boolean verified, @Nullable Boolean admin) {
            this.username = username;
            this.firstname = firstname;
            this.lastname = lastname;
            this.studentId = studentId;
            this.email = email;
            this.language = language;
            this.verified = verified != null && verified;
            this.admin = admin != null && admin;
        }

        // Some checks are done only if the value is changed, see the controller method.
        @NotNull /*@Pattern(regexp = USERNAME_REGEX)*/ @Size(/*min = USERNAME_LENGTH_MIN,*/ max = USERNAME_LENGTH_MAX) String username;
        @NotBlank @Size(max = FIRSTNAME_LENGTH_MAX) String firstname;
        @NotBlank @Size(max = LASTNAME_LENGTH_MAX) String lastname;
        @Nullable String studentId;
        @NotBlank @Email @Size(max = EMAIL_LENGTH_MAX) String email;
        @Nullable String language;
        boolean verified;
        boolean admin;
    }

    @Value
    public static class ChangePasswordForm {
        @Size(min = PASSWORD_LENGTH_MIN) String password;
        String password2;
    }

    @GetMapping("/user")
    @PreAuthorize("@accessChecker.isAdmin() || @accessChecker.isAssistantForAnyExercise()")
    public String getUsersPage(Model model) {
        metricsService.registerAccess();

        // The data is passed to the template in JSON format (for table rendering via JavaScript).
        // It is an array of arrays: the outer one for rows, the inner one for columns.
        Object[] data;

        // Admins can also see roles count (how often the user is student / tutor / assistant),
        // assistants only see some user data such that there is no need for the more expensive query.
        // The data array for the columns is smaller for non-admins.
        boolean isAdmin = accessChecker.isAdmin();
        if (isAdmin) {
            Users u = USERS.as("u");
            Students s = STUDENTS.as("s");
            Tutors t = TUTORS.as("t");
            Assistants a = ASSISTANTS.as("a");
            data = ctx
                .select(
                    u.USERID,
                    u.USERNAME,
                    u.STUDENTID,
                    u.FIRSTNAME,
                    u.LASTNAME,
                    u.EMAIL,
                    // admin-only:
                    DSL.select(DSL.count(s.EXERCISEID)).from(s).where(s.USERID.eq(u.USERID)).asField("studentRolesCount"),
                    DSL.select(DSL.count(t.EXERCISEID)).from(t).where(t.USERID.eq(u.USERID)).asField("tutorRolesCount"),
                    DSL.select(DSL.count(a.EXERCISEID)).from(a).where(a.USERID.eq(u.USERID)).asField("assistantRolesCount"),
                    u.ADMIN.convertFrom(admin -> admin ? 1 : 0)
                )
                .from(u)
                .fetchArrays();
        } else {
            data = ctx
                .select(
                    USERS.USERID,
                    USERS.USERNAME,
                    USERS.STUDENTID,
                    USERS.FIRSTNAME,
                    USERS.LASTNAME,
                    USERS.EMAIL
                )
                .from(USERS)
                .fetchArrays();
        }

        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("data", JsonUtils.toJson(Objects.requireNonNull(data)));
        return "user-administration/users";
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@accessChecker.isAdmin() || @accessChecker.isAssistantForAnyExercise()")
    public String getUserPage(@PathVariable int userId, Model model) {
        metricsService.registerAccess();

        IUser user = ctx.fetchOptional(USERS, USERS.USERID.eq(userId)).orElseThrow(NotFoundException::new);
        List<String> studentExercises = ctx
            .select(STUDENTS.EXERCISEID)
            .from(STUDENTS)
            .where(STUDENTS.USERID.eq(userId))
            .fetch(Record1::value1);
        studentExercises.sort(Comparators.IDENTIFIER);
        List<String> tutorExercises = ctx
            .select(TUTORS.EXERCISEID)
            .from(TUTORS)
            .where(TUTORS.USERID.eq(userId))
            .fetch(Record1::value1);
        tutorExercises.sort(Comparators.IDENTIFIER);
        List<String> assistantExercises = ctx
            .select(ASSISTANTS.EXERCISEID)
            .from(ASSISTANTS)
            .where(ASSISTANTS.USERID.eq(userId))
            .fetch(Record1::value1);
        assistantExercises.sort(Comparators.IDENTIFIER);

        // Non-admin users (assistants) can only see roles for exercises where they are assistants.
        boolean isAdmin = accessChecker.isAdmin();
        if (!isAdmin) {
            Set<String> myExercises = ctx
                .select(ASSISTANTS.EXERCISEID)
                .from(ASSISTANTS)
                .where(ASSISTANTS.USERID.eq(accessChecker.getUserId()))
                .fetchSet(Record1::value1);
            studentExercises.retainAll(myExercises);
            tutorExercises.retainAll(myExercises);
            assistantExercises.retainAll(myExercises);
        }

        model.addAttribute("user", user);
        model.addAttribute("studentExercises", studentExercises);
        model.addAttribute("tutorExercises", tutorExercises);
        model.addAttribute("assistantExercises", assistantExercises);
        if (isAdmin) {
            model.addAttribute("supportedLanguages", msg.getSupportedLanguages());
            if (!model.containsAttribute("changePasswordForm")) {
                model.addAttribute(new ChangePasswordForm("", ""));
            }
            if (!model.containsAttribute("editUserForm")) {
                model.addAttribute(new EditUserForm(
                    StringUtils.defaultString(user.getUsername()), user.getFirstname(), user.getLastname(), user.getStudentId(),
                    user.getEmail(), user.getLanguage(), user.getActivationCode() == null, user.getAdmin()
                ));
            }
            return "user-administration/edit-user";
        }
        return "user-administration/show-user";
    }

    @PostMapping("/user/{userId}")
    @PreAuthorize("@accessChecker.isAdmin()")
    @Transactional
    public String editUser(@PathVariable int userId, @Valid EditUserForm editUserForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        UserRecord userRecord = ctx
            .selectFrom(USERS)
            .where(USERS.USERID.eq(userId))
            .forUpdate()
            .fetchOptional()
            .orElseThrow(NotFoundException::new);

        // Convert empty String to null
        String username = StringUtils.defaultIfEmpty(editUserForm.username, null);
        String studentId = StringUtils.defaultIfEmpty(editUserForm.studentId, null);
        String language = StringUtils.defaultIfEmpty(editUserForm.language, null);

        // Allow keeping unchanged values, even if they violate the constraints.
        if (username != null && !username.equals(userRecord.getUsername())) {
            if (username.length() < USERNAME_LENGTH_MIN) {
                bindingResult.rejectValue("username", "Size");
            }
            if (!Pattern.compile(USERNAME_REGEX).matcher(username).matches()) {
                bindingResult.rejectValue("username", "Pattern");
            }
        }
        if (studentId != null && !studentId.equals(userRecord.getStudentId())
            && !exclaimProperties.getValidation().getStudentIdRegex().matcher(studentId).matches()
        ) {
            bindingResult.rejectValue("studentId", "StudentId");
        }
        if (language != null && !language.equals(userRecord.getLanguage())
            && !msg.getSupportedLanguages().containsKey(language)
        ) {
            bindingResult.rejectValue("language", "Invalid");
        }

        if (bindingResult.hasErrors()) {
            redirectBindingResult(editUserForm, bindingResult, redirectAttributes);
            addRedirectMessage(MessageType.ERROR, msg.getMessage("common.invalid-form-not-saved"), redirectAttributes);
        } else {
            userRecord.setUsernameIfChanged(editUserForm.username);
            userRecord.setFirstnameIfChanged(editUserForm.firstname);
            userRecord.setLastnameIfChanged(editUserForm.lastname);
            userRecord.setStudentIdIfChanged(studentId);
            userRecord.setEmailIfChanged(editUserForm.email);
            userRecord.setLanguageIfChanged(language);
            if (editUserForm.verified) {
                userRecord.setActivationCodeIfChanged(null);
            }
            userRecord.setAdminIfChanged(editUserForm.admin);
            if (userRecord.changed()) {
                // Get original values for logging, since .original() is set to new values after successful .update()
                UserRecord originalUserRecord = userRecord.original();

                try {
                    userRecord.update();
                    log.info("User data for user {} has been changed to {} (by admin {})", originalUserRecord, userRecord, accessChecker.getUser());
                    addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
                } catch (DuplicateKeyException e) {
                    if (!Objects.equals(editUserForm.username, originalUserRecord.getUsername())
                        && ctx.selectOne().from(USERS).where(USERS.USERNAME.eq(editUserForm.username)).fetchOne() != null
                    ) {
                        bindingResult.rejectValue("username", "Unique");
                    }
                    if (studentId != null && !studentId.equals(originalUserRecord.getStudentId())
                        && ctx.selectOne().from(USERS).where(USERS.STUDENTID.eq(studentId)).fetchOne() != null
                    ) {
                        bindingResult.rejectValue("studentId", "Unique");
                    }

                    redirectBindingResult(editUserForm, bindingResult, redirectAttributes);
                    if (bindingResult.hasErrors()) {
                        addRedirectMessage(MessageType.ERROR, msg.getMessage("common.invalid-form-not-saved"), redirectAttributes);
                    } else {
                        // Unexpected, just show the exception message to the admin user.
                        addRedirectMessage(MessageType.ERROR, e.toString(), redirectAttributes);
                    }
                }
            }
        }
        return "redirect:/user/{userId}";
    }

    @PostMapping("/user/{userId}/password")
    @PreAuthorize("@accessChecker.isAdmin()")
    @Transactional
    public String changePassword(@PathVariable int userId, @Valid ChangePasswordForm changePasswordForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!Objects.equals(changePasswordForm.password, changePasswordForm.password2)) {
            bindingResult.rejectValue("password2", "NotMatch");
        }
        if (bindingResult.hasErrors()) {
            redirectBindingResult(changePasswordForm, bindingResult, redirectAttributes);
            addRedirectMessage(MessageType.ERROR, msg.getMessage("common.invalid-form-not-saved"), redirectAttributes);
        } else {
            UserRecord userRecord =
                ctx
                    .selectFrom(USERS)
                    .where(USERS.USERID.eq(userId))
                    .forUpdate()
                    .fetchOptional()
                    .orElseThrow(NotFoundException::new);
            userRecord.setPassword(pe.encode(changePasswordForm.password));
            userRecord.update();
            log.info("Password for user {} has been changed by admin {}", userRecord, accessChecker.getUser());
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }
        return "redirect:/user/{userId}";
    }

    @PostMapping("/user/{userId}/delete")
    @PreAuthorize("@accessChecker.isAdmin()")
    @Transactional
    public String deleteUser(@PathVariable int userId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        UserRecord userRecord = ctx
            .selectFrom(USERS)
            .where(USERS.USERID.eq(userId))
            .forUpdate()
            .fetchOptional()
            .orElseThrow(NotFoundException::new);
        try {
            userRecord.delete();
            log.info("User {} has been deleted by admin {}", userRecord, accessChecker.getUser());
        } catch (DataIntegrityViolationException e) {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("user-administration.delete-account-failed") + "\n" + e, redirectAttributes);
            return "redirect:/user/{userId}";
        }
        addRedirectMessage(MessageType.SUCCESS, msg.getMessage("user-administration.delete-account-success"), redirectAttributes);
        return "redirect:/user";
    }

    @PostMapping("/user/{userId}/impersonate")
    @PreAuthorize("@accessChecker.isAdmin()")
    public String impersonateUser(@PathVariable int userId, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        UserRecord userRecord = ctx.fetchOne(USERS, USERS.USERID.eq(userId));
        if (userRecord == null) {
            throw new NotFoundException();
        }
        log.info("Admin user {} impersonates user {}", accessChecker.getUser(), userRecord);
        SecurityContextHolder.getContext().setAuthentication(new ExclaimAuthentication(new ExclaimUserPrincipal(userRecord)));
        addRedirectMessage(MessageType.SUCCESS, "You are now impersonating user " + userRecord, redirectAttributes);
        return "redirect:/";
    }
}
