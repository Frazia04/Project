package de.rptu.cs.exclaim.i18n;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.BindStatus;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
public class ICUMessageSourceImpl implements ICUMessageSource, BeanClassLoaderAware {
    private final String baseName;

    /**
     * The time zone to use for formatting date and time values.
     */
    private final ZoneId timezone;

    /**
     * The MultipleLocalesResolver to use when Thymeleaf asks for translations of binding error message.
     * TODO: Remove when not using Thymeleaf anymore
     */
    private final MultipleLocalesResolver multipleLocalesResolver;

    /**
     * Cache to hold already loaded ResourceBundles, keyed by locale.
     */
    private final ConcurrentHashMap<Locale, Optional<ResourceBundle>> cachedResourceBundles = new ConcurrentHashMap<>();

    /**
     * Cache to hold already generated MessageFormats, keyed by raw message.
     */
    private final ConcurrentHashMap<String, Map<Locale, MessageFormat>> cachedMessageFormats = new ConcurrentHashMap<>();

    @Nullable
    private ClassLoader classLoader;


    // -----------------------------------------------------------------------------------------------------------------
    // ICUMessageSource

    @Override
    @Nullable
    public String getMessage(String code, @Nullable Map<String, Object> args, @Nullable String defaultMessage, Locale locale) {
        return getMessageInternal(code, args, locale).orElse(defaultMessage);
    }

    @Override
    public String getMessage(String code, @Nullable Map<String, Object> args, Locale locale) throws NoSuchMessageException {
        return getMessageInternal(code, args, locale).orElseThrow(() -> new NoSuchMessageException(code, locale));
    }


    // -----------------------------------------------------------------------------------------------------------------
    // MessageSource

    @Override
    @Nullable
    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
        return getMessageInternal(code, args, locale).orElse(defaultMessage);
    }

    @Override
    public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
        return getMessageInternal(code, args, locale).orElseThrow(() -> new NoSuchMessageException(code, locale));
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        String[] codes = resolvable.getCodes();
        Object[] args = resolvable.getArguments();
        if (codes != null) {
            if (isCalledFromBindStatus()) {
                // TODO: Remove when not using Thymeleaf anymore
                // Problem: When Thymeleaf renders binding errors, it takes only one locale into consideration.
                // We ignore the specified locale and instead use all locales provided by our MultipleLocalesResolver.
                if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                    List<Locale> locales = multipleLocalesResolver.resolveLocales(sra.getRequest());

                    String message = Arrays.stream(codes)
                        .flatMap(code -> locales.stream()
                            .map(locale1 -> getMessage(code, args, null, locale1))
                            .filter(Objects::nonNull)
                            .limit(1)
                        ).findFirst().orElseGet(resolvable::getDefaultMessage);
                    if (message != null) {
                        return message;
                    } else {
                        log.error("No message found for codes {} in locales {}", codes, locales);
                    }
                } else {
                    log.error("Could not get current request!");
                }

                // Not having a message here is bad (there should be at least one in the default language at the end of
                // the locales list), but we do not want to break Thymeleaf rendering if it happens. Instead, we log the
                // error (see above) and return something useful (the least-specific code).
                return codes[codes.length - 1];
            }
            String message = Arrays.stream(codes)
                .flatMap(code -> getMessageInternal(code, args, locale).stream())
                .findFirst().orElseGet(resolvable::getDefaultMessage);
            if (message != null) {
                return message;
            }
            throw new NoSuchMessageException(codes[codes.length - 1], locale);
        }
        throw new NoSuchMessageException("", locale);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Private helpers, BeanClassLoaderAware

    private ULocale addTimezone(Locale locale) {
        return new ULocale(locale + "@timezone=" + timezone.getId());
    }

    @SuppressWarnings("JdkObsolete")
    private Optional<String> getMessageInternal(String code, @Nullable Map<String, Object> args, Locale locale) {
        return getRawMessage(code, locale).map(
            rawMessage -> (args == null || args.isEmpty()) ? rawMessage :
                cachedMessageFormats
                    .computeIfAbsent(rawMessage, m -> new ConcurrentHashMap<>())
                    .computeIfAbsent(locale, locale1 -> new MessageFormat(rawMessage, addTimezone(locale1)))
                    .format(args, new StringBuffer(), null).toString()
        );
    }

    @SuppressWarnings("JdkObsolete")
    private Optional<String> getMessageInternal(String code, @Nullable Object[] args, Locale locale) {
        return getRawMessage(code, locale).map(
            rawMessage -> (args == null || args.length == 0) ? rawMessage :
                cachedMessageFormats
                    .computeIfAbsent(rawMessage, m -> new ConcurrentHashMap<>())
                    .computeIfAbsent(locale, locale1 -> new MessageFormat(rawMessage, addTimezone(locale1)))
                    .format(args, new StringBuffer(), null).toString()
        );
    }

    private Optional<String> getRawMessage(String code, Locale locale) {
        return getBundle(locale).map(
            resourceBundle -> {
                try {
                    return resourceBundle.getString(code);
                } catch (MissingResourceException e) {
                    // This happens a lot for JSR 380 validation annotations
                    log.debug("Message bundle for locale {} exists but is missing key {}", locale, code);
                    return null;
                }
            }
        );
    }

    private Optional<ResourceBundle> getBundle(Locale locale) throws MissingResourceException {
        Assert.state(classLoader != null, "No ClassLoader set");
        return cachedResourceBundles.computeIfAbsent(locale, locale1 -> {
            try {
                return Optional.of(ResourceBundle.getBundle(baseName, locale1, classLoader, MessageSourceControl.INSTANCE));
            } catch (MissingResourceException e) {
                // This is Ok. We will check the next entry in the Accept-Language HTTP header,
                // ultimately falling back to a default (and thus available) locale.
                return Optional.empty();
            }
        });
    }

    private static final String BIND_STATUS_CLASS_NAME = BindStatus.class.getCanonicalName();

    private static boolean isCalledFromBindStatus() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        // 0: Thread
        // 1: this private method
        // 2: caller from this class
        // 3: external caller
        int limit = Math.min(10, trace.length);
        for (int i = 3; i < limit; i++) {
            if (trace[i].getClassName().equals(BIND_STATUS_CLASS_NAME)
                && trace[i].getMethodName().startsWith("getErrorMessage")
            ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    private static class MessageSourceControl extends ResourceBundle.Control {
        private static final MessageSourceControl INSTANCE = new MessageSourceControl();

        @Override
        public List<String> getFormats(String baseName) {
            return ResourceBundle.Control.FORMAT_PROPERTIES;
        }

        @Override
        @Nullable
        public Locale getFallbackLocale(String baseName, Locale locale) {
            // No fallback for the whole bundle, since we check multiple locales for each individual key.
            return null;
        }
    }
}
