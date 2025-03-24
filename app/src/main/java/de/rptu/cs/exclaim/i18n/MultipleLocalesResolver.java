package de.rptu.cs.exclaim.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Extension of the {@link LocaleResolver} interface that can resolve to a list of multiple locales,
 * e.g. for multiple entries in the Accept-Language HTTP header.
 */
public interface MultipleLocalesResolver extends LocaleResolver {
    /**
     * Determine the locales that are acceptable to the client.
     *
     * @param request the request to resolve the locales for
     * @return a list of acceptable locale, starting with the highest precedence, never null or empty
     */
    List<Locale> resolveLocales(HttpServletRequest request);
}
