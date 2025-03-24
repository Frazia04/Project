package de.rptu.cs.exclaim.i18n;

import de.rptu.cs.exclaim.data.interfaces.IUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Collection of methods to access translations.
 * <p>
 * There are different method variants for translations with named parameters (Map), numeric parameters (Array), without
 * parameters and using a MessageSourceResolvable.
 * <p>
 * The methods with a defaultMessage parameter (which may be null) return that defaultMessage if no suitable translation
 * can be found. The other methods will throw an exception in that case.
 * <p>
 * The methods without locale or user parameter get the locale from the current HTTP request context, which is desirable
 * in most cases. The methods taking a user parameter consider the language saved in the given user object (useful in
 * background jobs that do not run within the context of an HTTP request). The methods taking a locale parameter look up
 * translations for the given locale.
 */
@RequiredArgsConstructor
public class ICUMessageSourceAccessor {
    @RequiredArgsConstructor
    public static class UserLocale {
        @Nullable private final String locale;
    }

    private static final String ATTRIBUTE_NAME_CACHE_BEST_LOCALE = ICUMessageSourceAccessor.class.getName() + ".CACHE_BEST_LOCALE";
    private final ICUMessageSource icuMessageSource;
    private final MultipleLocalesResolver multipleLocalesResolver;
    private final UserLocalesResolver userLocalesResolver;

    /**
     * Map from language key (xy in messages_xy.properties file name) human-readable language name in that language.
     */
    @Getter
    private final Map<String, String> supportedLanguages;

    private static HttpServletRequest getRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        throw new IllegalStateException("Could not get current request!");
    }

    private List<Locale> getLocales() {
        return multipleLocalesResolver.resolveLocales(getRequest());
    }

    /**
     * Get the best supported locale the current http request.
     *
     * @return the best supported locale
     */
    public Locale getBestSupportedLocale() {
        HttpServletRequest request = getRequest();
        Locale bestLocale = (Locale) request.getAttribute(ATTRIBUTE_NAME_CACHE_BEST_LOCALE);
        if (bestLocale == null) {
            for (Locale locale : getLocales()) {
                if (supportedLanguages.containsKey(locale.getLanguage().toLowerCase(Locale.ROOT))) {
                    bestLocale = locale;
                    break;
                }
            }
            if (bestLocale == null) {
                throw new IllegalStateException("Could not determine the best locale!");
            }
            request.setAttribute(ATTRIBUTE_NAME_CACHE_BEST_LOCALE, bestLocale);
        }
        return bestLocale;
    }

    /**
     * Get the best language for the current http request.
     *
     * @return the language key
     */
    public String getBestLanguage() {
        return getBestSupportedLocale().getLanguage().toLowerCase(Locale.ROOT);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Arguments Map

    @Nullable
    public String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage, Locale locale) {
        return icuMessageSource.getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, @Nullable Map<String, Object> args, Locale locale) throws NoSuchMessageException {
        return icuMessageSource.getMessage(code, args, locale);
    }

    private Optional<String> getMessage(String code, @Nullable Map<String, Object> args, List<Locale> locales) {
        return locales.stream()
            .map(locale -> getMessage(code, args, null, locale))
            .filter(Objects::nonNull)
            .findFirst();
    }

    @Nullable
    public String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage) {
        return getMessage(code, args, getLocales()).orElse(defaultMessage);
    }

    public String getMessage(String code, @Nullable Map<String, Object> args) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = getLocales();
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }

    @Nullable
    public String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage, IUser user) {
        return getMessage(code, args, userLocalesResolver.resolveLocales(user.getLanguage())).orElse(defaultMessage);
    }

    @Nullable
    public String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage, UserLocale userLocale) {
        return getMessage(code, args, userLocalesResolver.resolveLocales(userLocale.locale)).orElse(defaultMessage);
    }

    public String getMessage(String code, @Nullable Map<String, Object> args, IUser user) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = userLocalesResolver.resolveLocales(user.getLanguage());
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }

    public String getMessage(String code, @Nullable Map<String, Object> args, UserLocale userLocale) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = userLocalesResolver.resolveLocales(userLocale.locale);
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Arguments Array

    @Nullable
    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
        return icuMessageSource.getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
        return icuMessageSource.getMessage(code, args, locale);
    }

    private Optional<String> getMessage(String code, @Nullable Object[] args, List<Locale> locales) {
        return locales.stream()
            .map(locale -> getMessage(code, args, null, locale))
            .filter(Objects::nonNull)
            .findFirst();
    }

    @Nullable
    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage) {
        return getMessage(code, args, getLocales()).orElse(defaultMessage);
    }

    public String getMessage(String code, @Nullable Object[] args) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = getLocales();
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }

    @Nullable
    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, IUser user) {
        return getMessage(code, args, userLocalesResolver.resolveLocales(user.getLanguage())).orElse(defaultMessage);
    }

    @Nullable
    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, UserLocale userLocale) {
        return getMessage(code, args, userLocalesResolver.resolveLocales(userLocale.locale)).orElse(defaultMessage);
    }

    public String getMessage(String code, @Nullable Object[] args, IUser user) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = userLocalesResolver.resolveLocales(user.getLanguage());
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }

    public String getMessage(String code, @Nullable Object[] args, UserLocale userLocale) throws NoSuchMessageInAnyLocaleException {
        List<Locale> locales = userLocalesResolver.resolveLocales(userLocale.locale);
        return getMessage(code, args, locales).orElseThrow(() -> new NoSuchMessageInAnyLocaleException(code, locales));
    }


    // -----------------------------------------------------------------------------------------------------------------
    // No Arguments

    @Nullable
    public String getMessage(String code, @Nullable String defaultMessage, Locale locale) {
        return getMessage(code, (Object[]) null, defaultMessage, locale);
    }

    public String getMessage(String code, Locale locale) throws NoSuchMessageException {
        return getMessage(code, (Object[]) null, locale);
    }

    @Nullable
    public String getMessage(String code, @Nullable String defaultMessage) {
        return getMessage(code, (Object[]) null, defaultMessage);
    }

    public String getMessage(String code) throws NoSuchMessageInAnyLocaleException {
        return getMessage(code, (Object[]) null);
    }

    @Nullable
    public String getMessage(String code, @Nullable String defaultMessage, IUser user) {
        return getMessage(code, (Object[]) null, defaultMessage, user);
    }

    @Nullable
    public String getMessage(String code, @Nullable String defaultMessage, UserLocale userLocale) {
        return getMessage(code, (Object[]) null, defaultMessage, userLocale);
    }

    public String getMessage(String code, IUser user) throws NoSuchMessageInAnyLocaleException {
        return getMessage(code, (Object[]) null, user);
    }

    public String getMessage(String code, UserLocale userLocale) throws NoSuchMessageInAnyLocaleException {
        return getMessage(code, (Object[]) null, userLocale);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // MessageSourceResolvable

    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        return icuMessageSource.getMessage(resolvable, locale);
    }

    private String getMessage(MessageSourceResolvable resolvable, List<Locale> locales) throws NoSuchMessageInAnyLocaleException {
        String[] codes = resolvable.getCodes();
        Object[] args = resolvable.getArguments();
        if (codes != null) {
            String message = Arrays.stream(codes)
                .flatMap(code -> locales.stream()
                    .map(locale -> getMessage(code, args, null, locale))
                    .filter(Objects::nonNull)
                    .limit(1)
                ).findFirst().orElseGet(resolvable::getDefaultMessage);
            if (message != null) {
                return message;
            }
            throw new NoSuchMessageInAnyLocaleException(codes[codes.length - 1], locales);
        }
        throw new NoSuchMessageInAnyLocaleException("", locales);
    }

    public String getMessage(MessageSourceResolvable resolvable) throws NoSuchMessageInAnyLocaleException {
        return getMessage(resolvable, getLocales());
    }

    public String getMessage(MessageSourceResolvable resolvable, IUser user) throws NoSuchMessageInAnyLocaleException {
        return getMessage(resolvable, userLocalesResolver.resolveLocales(user.getLanguage()));
    }

    public String getMessage(MessageSourceResolvable resolvable, UserLocale userLocale) throws NoSuchMessageInAnyLocaleException {
        return getMessage(resolvable, userLocalesResolver.resolveLocales(userLocale.locale));
    }
}
