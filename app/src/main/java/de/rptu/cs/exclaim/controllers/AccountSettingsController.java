package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.i18n.CookieLocalesResolver;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExclaimPasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Objects;

import static de.rptu.cs.exclaim.ExclaimValidationProperties.PASSWORD_LENGTH_MIN;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addRedirectMessage;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.redirectBindingResult;

/**
 * Allow all users to change settings in their own account.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class AccountSettingsController {
    private final ICUMessageSourceAccessor msg;
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final ExclaimPasswordEncoder pe;
    private final CookieLocalesResolver cookieLocalesResolver;

    @Value
    public static class ChangePasswordForm {
        @NotNull String oldPassword;
        @NotNull @Size(min = PASSWORD_LENGTH_MIN) String password;
        @NotNull String password2;
    }

    @GetMapping("/settings")
    public String getSettingsPage(Model model) {
        metricsService.registerAccess();

        // Get the user's language. If there is none in the database, get it from the http request header.
        UserRecord userRecord = accessChecker.getUser();
        String language = userRecord.getLanguage();
        if (language == null) {
            language = msg.getBestLanguage();
        }

        model.addAttribute("user", userRecord);
        model.addAttribute("language", language);
        model.addAttribute("supportedLanguages", msg.getSupportedLanguages());
        model.addAttribute("hasPassword", userRecord.getPassword() != null);
        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute(new ChangePasswordForm("", "", ""));
        }
        return "account/settings";
    }

    @PostMapping("/settings")
    public String changeLanguage(@RequestParam String language, RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        metricsService.registerAccess();
        UserRecord userRecord = accessChecker.getUser();
        if (!msg.getSupportedLanguages().containsKey(language)) {
            addRedirectMessage(MessageType.ERROR, "Invalid language!", redirectAttributes);
        } else {
            userRecord.setLanguageIfChanged(language);
            if (userRecord.changed()) {
                userRecord.update();
            }
            cookieLocalesResolver.setLocale(request, response, Locale.forLanguageTag(language));
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@Valid ChangePasswordForm changePasswordForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        metricsService.registerAccess();
        UserRecord userRecord = accessChecker.getUser();
        if (!Objects.equals(changePasswordForm.password, changePasswordForm.password2)) {
            bindingResult.rejectValue("password2", "NotMatch");
        }
        if (!bindingResult.hasFieldErrors("oldPassword")) {
            String passwordHash = userRecord.getPassword();
            if (passwordHash == null || !pe.matches(changePasswordForm.oldPassword, passwordHash)) {
                bindingResult.rejectValue("oldPassword", "Invalid");
            }
        }
        if (bindingResult.hasErrors()) {
            redirectBindingResult(changePasswordForm, bindingResult, redirectAttributes);
            addRedirectMessage(MessageType.ERROR, msg.getMessage("common.invalid-form-not-saved"), redirectAttributes);
        } else {
            userRecord.setPassword(pe.encode(changePasswordForm.password));
            userRecord.update();
            log.info("Password for user {} has been changed via settings", userRecord);
            addRedirectMessage(MessageType.SUCCESS, msg.getMessage("common.saved"), redirectAttributes);
            accessChecker.updatePassword(userRecord);
        }
        return "redirect:/settings";
    }
}
