package de.rptu.cs.exclaim.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.lang.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Extension of the {@link MessageSource} interface that has support for passing arguments as Map.
 */
public interface ICUMessageSource extends MessageSource {
    /**
     * Try to resolve the message. Return default message if no message was found.
     *
     * @param code           the message code to look up, e.g. 'calculator.noRateSet'.
     *                       MessageSource users are encouraged to base message names on qualified class
     *                       or package names, avoiding potential conflicts and ensuring maximum clarity.
     * @param args           a map of arguments that will be filled in for params within the message
     *                       (params look like "{name}", "{birthday,date}", "{start,time}" within a message),
     *                       or {@code null} if none
     * @param defaultMessage a default message to return if the lookup fails
     * @param locale         the locale in which to do the lookup
     * @return the resolved message if the lookup was successful,
     * otherwise the default message passed as a parameter (which may be {@code null})
     * @see com.ibm.icu.text.MessageFormat
     */
    @Nullable
    String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage, Locale locale);

    /**
     * Try to resolve the message. Treat as an error if the message can't be found.
     *
     * @param code   the message code to look up, e.g. 'calculator.noRateSet'.
     *               MessageSource users are encouraged to base message names on qualified class
     *               or package names, avoiding potential conflicts and ensuring maximum clarity.
     * @param args   a map of arguments that will be filled in for params within the message
     *               (params look like "{name}", "{birthday,date}", "{start,time}" within a message),
     *               or {@code null} if none
     * @param locale the locale in which to do the lookup
     * @return the resolved message (never {@code null})
     * @throws NoSuchMessageException if no corresponding message was found
     * @see com.ibm.icu.text.MessageFormat
     */
    String getMessage(String code, @Nullable Map<String, Object> args, Locale locale) throws NoSuchMessageException;
}
