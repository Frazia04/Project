package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.jobs.BackgroundJobExecutor;
import de.rptu.cs.exclaim.jobs.SendAccountActivationMail;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import de.rptu.cs.exclaim.security.SecurityConfig.PublicPath;
import de.rptu.cs.exclaim.utils.RandomTokenGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.EMAIL_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.FIRSTNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.LASTNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.PASSWORD_LENGTH_MIN;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_LENGTH_MAX;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_LENGTH_MIN;
import static de.rptu.cs.exclaim.ExclaimValidationProperties.USERNAME_REGEX;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static de.rptu.cs.exclaim.security.SecurityConfig.LOGIN_PAGE;

/**
 * Allow guests to create a new account.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class RegistrationController {
    public static final String REGISTER_PATH = "/register";
    public static final String ACTIVATE_PATH = "/activate";

    @Bean
    public static PublicPath registerAndActivatePaths() {
        return new PublicPath(REGISTER_PATH, ACTIVATE_PATH);
    }

    private final ExclaimProperties exclaimProperties;
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;
    private final ExclaimPasswordEncoder exclaimPasswordEncoder;
    private final RandomTokenGenerator randomTokenGenerator;
    private final SendAccountActivationMail sendAccountActivationMail;
    private final BackgroundJobExecutor backgroundJobExecutor;

    @Value
    public static class RegistrationForm {
        @NotNull @Pattern(regexp = USERNAME_REGEX) @Size(min = USERNAME_LENGTH_MIN, max = USERNAME_LENGTH_MAX) String username;
        @NotBlank @Size(max = FIRSTNAME_LENGTH_MAX) String firstname;
        @NotBlank @Size(max = LASTNAME_LENGTH_MAX) String lastname;
        @Nullable String studentId;
        @NotBlank @Email @Size(max = EMAIL_LENGTH_MAX) String email;
        String email2;
        @NotNull String language;
        @NotNull @Size(min = PASSWORD_LENGTH_MIN) String password;
        String password2;
    }

    private String redirectHome() {
        return "redirect:" + (accessChecker.isAuthenticated() ? "/" : LOGIN_PAGE);
    }

    @GetMapping(REGISTER_PATH)
    public String getRegistrationPage(Model model) {
        metricsService.registerAccess();
        model.addAttribute(new RegistrationForm("", "", "", "", "", "", msg.getBestLanguage(), "", ""));
        model.addAttribute("supportedLanguages", msg.getSupportedLanguages());
        return "account/register";
    }

    @PostMapping(REGISTER_PATH)
    public String register(@Valid RegistrationForm registrationForm, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        String studentId = StringUtils.defaultIfEmpty(registrationForm.studentId, null);
        if (!Objects.equals(registrationForm.email, registrationForm.email2)) {
            bindingResult.rejectValue("email2", "NotMatch");
        }
        if (!Objects.equals(registrationForm.password, registrationForm.password2)) {
            bindingResult.rejectValue("password2", "NotMatch");
        }
        if (studentId != null && !exclaimProperties.getValidation().getStudentIdRegex().matcher(studentId).matches()) {
            bindingResult.rejectValue("studentId", "StudentId");
        }
        Map<String, String> supportedLanguages = msg.getSupportedLanguages();
        model.addAttribute("supportedLanguages", supportedLanguages);
        if (!supportedLanguages.containsKey(registrationForm.language)) {
            bindingResult.rejectValue("language", "Invalid");
        }
        if (bindingResult.hasErrors()) {
            return "account/register";
        }

        UserRecord userRecord = ctx.newRecord(USERS);
        userRecord.setUsername(registrationForm.username);
        userRecord.setFirstname(registrationForm.firstname);
        userRecord.setLastname(registrationForm.lastname);
        userRecord.setStudentId(studentId);
        userRecord.setEmail(registrationForm.email);
        userRecord.setPassword(exclaimPasswordEncoder.encode(registrationForm.password));
        userRecord.setLanguage(registrationForm.language);
        if (!exclaimProperties.getBypassNewUserActivation()) {
            userRecord.setActivationCode(randomTokenGenerator.generate());
        }

        try {
            userRecord.insert();
        } catch (DuplicateKeyException e) {
            // Find the user(s) responsible for the duplicate key (same username/studentId)
            List<UserRecord> duplicateUsers = ctx
                .selectFrom(USERS)
                .where(DSL.or(
                    USERS.USERNAME.eq(registrationForm.username),
                    studentId != null ? USERS.STUDENTID.eq(studentId) : DSL.noCondition()
                ))
                .forUpdate()
                .fetch();

            // Should not happen: DuplicateKeyException without finding the duplicate
            if (duplicateUsers.isEmpty()) {
                throw e; // propagate the DuplicateKeyException
            }

            ListIterator<UserRecord> iterator = duplicateUsers.listIterator();
            while (iterator.hasNext()) {
                UserRecord existingUserRecord = iterator.next();

                // Check which fields are duplicated
                List<String> duplicateFields = new ArrayList<>(2);
                if (registrationForm.username.equals(existingUserRecord.getUsername())) {
                    duplicateFields.add("username");
                }
                if (studentId != null && studentId.equals(existingUserRecord.getStudentId())) {
                    duplicateFields.add("studentId");
                }

                // If the user is not yet activated, try to delete the existing account
                if (existingUserRecord.getActivationCode() != null) {
                    try {
                        existingUserRecord.delete();
                        iterator.remove();
                        log.info("Deleted not yet activated account {} because of a new registration with the same {}", existingUserRecord, duplicateFields);
                        continue; // skip the rejectValue call below
                    } catch (DataIntegrityViolationException ignored) {
                        // Existing account might already be added to an exercise
                    }
                }

                // If the account is already activated or deletion failed, then reject the form fields
                duplicateFields.forEach(duplicateField -> bindingResult.rejectValue(duplicateField, "Unique"));
            }

            if (duplicateUsers.isEmpty()) {
                // We have deleted all duplicates and can retry the insert now.
                userRecord.insert(); // Should not throw another DuplicateKeyException (we would propagate it)
            } else {
                // There is a duplicate user that could not be deleted. We have added an error message to the form.
                return "account/register";
            }
        }

        // If we reach this point, the user has been inserted.
        log.info("Registered user {}", userRecord);
        if (userRecord.getActivationCode() != null) {
            sendAccountActivationMail.submit(userRecord.getUserId());
            backgroundJobExecutor.pollNow();
        }
        addRedirectMessage(MessageType.SUCCESS, msg.getMessage(
            userRecord.getActivationCode() == null ? "register.success" : "register.success-require-activation"
        ), redirectAttributes);
        return redirectHome();
    }

    @GetMapping(ACTIVATE_PATH)
    @Transactional
    public String activate(@RequestParam("user") String username, @RequestParam("code") String code, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        UserRecord userRecord = ctx
            .selectFrom(USERS)
            .where(USERS.USERNAME.eq(username))
            .forUpdate()
            .fetchOne();
        boolean isActive = false;
        if (userRecord != null) {
            String activationCode = userRecord.getActivationCode();
            if (activationCode == null) {
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("register.already-activated"), redirectAttributes);
                isActive = true;
            } else if (activationCode.equals(code)) {
                userRecord.setActivationCode(null);
                userRecord.update();
                log.info("User {} has been activated", userRecord);
                addRedirectMessage(MessageType.SUCCESS, msg.getMessage("register.activated"), redirectAttributes);
                isActive = true;
            }
        }
        if (!isActive) {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("register.invalid-activation-link", Map.of("adminContact", exclaimProperties.getAdminContact())), redirectAttributes);
        }
        return redirectHome();
    }
}
