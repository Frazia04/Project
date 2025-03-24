package de.rptu.cs.exclaim.controllers;

import org.springframework.core.Conventions;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility functions to be used in controllers, e.g. for adding messages.
 */
public class ControllerUtils {
    public enum MessageType {
        SUCCESS, WARNING, ERROR;

        public String messageKey() {
            return name().toLowerCase(Locale.ROOT) + "Messages";
        }
    }

    public static void addMessage(MessageType messageType, String message, Model model) {
        String key = messageType.messageKey();
        Object existing = model.getAttribute(key);
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            list.add(message);
        } else {
            List<String> list = new ArrayList<>();
            list.add(message);
            model.addAttribute(key, list);
        }
    }

    public static void addRedirectMessage(MessageType messageType, String message, RedirectAttributes redirectAttributes) {
        String key = messageType.messageKey();
        Object existing = redirectAttributes.getFlashAttributes().get(key);
        if (existing instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            list.add(message);
        } else {
            List<String> list = new ArrayList<>();
            list.add(message);
            redirectAttributes.addFlashAttribute(key, list);
        }
    }

    public static void redirectBindingResult(Object form, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        String name = Conventions.getVariableName(form);
        redirectAttributes.addFlashAttribute(name, form);
        redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + name, bindingResult);
    }
}
