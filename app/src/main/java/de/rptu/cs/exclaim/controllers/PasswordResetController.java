package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.data.records.PasswordResetRecord;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.jobs.BackgroundJobExecutor;
import de.rptu.cs.exclaim.jobs.SendPasswordResetMail;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.schema.tables.PasswordResets;
import de.rptu.cs.exclaim.schema.tables.Users;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import de.rptu.cs.exclaim.security.SecurityConfig.PublicPath;
import de.rptu.cs.exclaim.utils.RandomTokenGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.PASSWORD_LENGTH_MIN;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addMessage;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.schema.tables.PasswordResets.PASSWORD_RESETS;
import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static de.rptu.cs.exclaim.security.SecurityConfig.LOGIN_PAGE;

/**
 * Allow all users to reset their own password.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PasswordResetController {
    public static final String REQUEST_PATH = "/requestPassword";
    public static final String RESET_PATH = "/resetPassword";

    @Bean
    public static PublicPath passwordResetPaths() {
        return new PublicPath(REQUEST_PATH, RESET_PATH);
    }

    private final ExclaimProperties exclaimProperties;
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final ExclaimPasswordEncoder pe;
    private final RandomTokenGenerator randomTokenGenerator;
    private final DSLContext ctx;
    private final SendPasswordResetMail sendPasswordResetMail;
    private final BackgroundJobExecutor backgroundJobExecutor;

    @Value
    public static class ResetPasswordForm {
        int userId;
        @NotNull String code;
        @NotNull @Size(min = PASSWORD_LENGTH_MIN) String password;
        @NotNull String password2;
    }

    private String redirectHome() {
        return "redirect:" + (accessChecker.isAuthenticated() ? "/" : LOGIN_PAGE);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Phase 1: Send an e-mail containing a link

    @GetMapping(REQUEST_PATH)
    public String getRequestPasswordPage() {
        metricsService.registerAccess();
        return "account/request-password";
    }

    @Transactional
    @PostMapping(REQUEST_PATH)
    public String requestPassword(@RequestParam String email, Model model) {
        metricsService.registerAccess();
        List<UserRecord> users = email.isEmpty()
            ? Collections.emptyList()
            : ctx.fetch(USERS, DSL.lower(USERS.EMAIL).eq(email.toLowerCase(Locale.ROOT)).and(USERS.USERNAME.isNotNull()));
        if (users.isEmpty()) {
            addMessage(MessageType.ERROR, msg.getMessage("request-password.invalid-email"), model);
            model.addAttribute("email", email);
        } else {
            LocalDateTime validUntil = LocalDateTime.now(ZoneOffset.UTC).plus(exclaimProperties.getPasswordResetEmailValidity());
            boolean exists = false; // whether there is an existing record that is still valid
            boolean success = false; // whether we have created a new password reset record and sent an e-mail
            for (UserRecord userRecord : users) {
                boolean existsForThisUser = ctx
                    .selectOne()
                    .from(PASSWORD_RESETS)
                    .where(
                        PASSWORD_RESETS.USERID.eq(userRecord.getUserId()),
                        PASSWORD_RESETS.VALID_UNTIL.gt(LocalDateTime.now(ZoneOffset.UTC))
                    )
                    .fetchOne() != null;
                if (existsForThisUser) {
                    exists = true;
                } else {
                    String code = randomTokenGenerator.generate();
                    PasswordResetRecord passwordResetRecord = ctx.newRecord(PASSWORD_RESETS);
                    passwordResetRecord.setUserId(userRecord.getUserId());
                    passwordResetRecord.setCode(code);
                    passwordResetRecord.setValidUntil(validUntil);
                    passwordResetRecord.merge(); // replaces existing expired entry
                    sendPasswordResetMail.submit(userRecord.getUserId());
                    success = true;
                }
            }
            if (exists) {
                addMessage(MessageType.ERROR, msg.getMessage("request-password.existing-record", Map.of("adminContact", exclaimProperties.getAdminContact())), model);
            }
            if (success) {
                backgroundJobExecutor.pollNow();
                addMessage(MessageType.SUCCESS, msg.getMessage("request-password.mail-sent-successfully"), model);
            }
        }
        return "account/request-password";
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Phase 2: Reset the password using that link

    @Transactional
    @GetMapping(RESET_PATH)
    public String getResetPasswordPage(@RequestParam("user") String username, @RequestParam("reset") String code, Model model) {
        metricsService.registerAccess();
        PasswordResets p = PASSWORD_RESETS.as("p");
        Users u = p.user().as("u");
        UserRecord userRecord = ctx
            .select(u)
            .from(p)
            .where(
                u.USERNAME.eq(username),
                p.CODE.eq(code),
                p.VALID_UNTIL.ge(LocalDateTime.now(ZoneOffset.UTC))
            )
            .forUpdate()
            .fetchOne(Record1::value1);
        if (userRecord != null) {
            if (userRecord.getActivationCode() != null) {
                userRecord.setActivationCode(null);
                userRecord.update();
                log.info("User {} has been activated by accessing password reset link", userRecord);
                addMessage(MessageType.SUCCESS, msg.getMessage("register.activated"), model);
            }
            model.addAttribute(new ResetPasswordForm(userRecord.getUserId(), code, "", ""));
            return "account/reset-password";
        } else {
            addMessage(MessageType.ERROR, msg.getMessage("reset-password.invalid-link"), model);
            return "account/request-password";
        }
    }

    @PostMapping(RESET_PATH)
    @Transactional
    public String resetPassword(@Valid ResetPasswordForm resetPasswordForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        if (!Objects.equals(resetPasswordForm.password, resetPasswordForm.password2)) {
            bindingResult.rejectValue("password2", "NotMatch");
        }
        if (bindingResult.hasErrors()) {
            return "account/reset-password";
        }
        PasswordResets p = PASSWORD_RESETS.as("p");
        Users u = p.user().as("u");
        Record2<UserRecord, PasswordResetRecord> result = ctx
            .select(u, p)
            .from(p)
            .where(
                u.USERID.eq(resetPasswordForm.userId),
                p.CODE.eq(resetPasswordForm.code),
                p.VALID_UNTIL.ge(LocalDateTime.now(ZoneOffset.UTC))
            )
            .forUpdate()
            .fetchOne();
        if (result != null) {
            UserRecord userRecord = result.value1();
            PasswordResetRecord passwordResetRecord = result.value2();
            userRecord.setPassword(pe.encode(resetPasswordForm.password));
            userRecord.update();
            passwordResetRecord.delete();
            log.info("Password for user {} has been changed via password reset", userRecord);
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("reset-password.success"), redirectAttributes);
            return redirectHome();
        } else {
            addRedirectMessage(MessageType.ERROR, msg.getMessage("reset-password.invalid-link"), redirectAttributes);
            return "redirect:" + REQUEST_PATH;
        }
    }
}
