package de.rptu.cs.exclaim.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Similar to the {@link MultipleLocalesResolver} interface, but instead of an HTTP request we need to know the user.
 * Suitable for background jobs that run for a specific user, but not within the context of an HTTP request.
 */
@Slf4j
public class UserLocalesResolver {
    private final List<Locale> defaultLocales;

    public UserLocalesResolver(List<Locale> defaultLocales) {
        this.defaultLocales = new ArrayList<>(defaultLocales);
        Assert.notEmpty(this.defaultLocales, "There must be at least one default locale as ultimate fallback");
    }

    /**
     * Determine the locales that are acceptable to the user.
     *
     * @param userLocale the locale that user prefers (from the database), may be null if unknown
     * @return a list of acceptable locales, starting with the highest precedence, never null or empty
     */
    public List<Locale> resolveLocales(@Nullable String userLocale) {
        if (userLocale == null) {
            return defaultLocales;
        }

        // Avoid duplicate entries but preserve order
        LinkedHashSet<Locale> locales = new LinkedHashSet<>();
        locales.add(Locale.forLanguageTag(userLocale));
        locales.addAll(defaultLocales);

        List<Locale> result = new ArrayList<>(locales);
        log.debug("resolved locales: {}", result);
        return result;
    }
}
