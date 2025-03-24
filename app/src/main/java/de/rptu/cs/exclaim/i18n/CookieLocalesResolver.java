package de.rptu.cs.exclaim.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.util.WebUtils;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@link LocaleResolver} similar to {@link CookieLocaleResolver},
 * but implementing the {@link MultipleLocalesResolver} interface.
 */
@Slf4j
public class CookieLocalesResolver implements MultipleLocalesResolver {
    private static final String ATTRIBUTE_NAME_CACHE = CookieLocalesResolver.class.getName() + ".CACHE";
    private static final String ATTRIBUTE_NAME_SET = CookieLocalesResolver.class.getName() + ".SET";
    private static final String ATTRIBUTE_NAME_UNSET = CookieLocalesResolver.class.getName() + ".UNSET";
    private final List<Locale> defaultLocales;
    private final ResponseCookie cookie;

    public CookieLocalesResolver(List<Locale> defaultLocales, ResponseCookie cookie) {
        Assert.notEmpty(defaultLocales, "There must be at least one default locale as ultimate fallback");
        this.defaultLocales = new ArrayList<>(defaultLocales);
        this.cookie = cookie;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return loadFromCacheOrResolveLocales(request)[0];
    }

    @Override
    public List<Locale> resolveLocales(HttpServletRequest request) {
        return List.of(loadFromCacheOrResolveLocales(request));
    }

    @Override
    public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
        log.debug("setLocale: {}", locale);
        if (response == null) {
            throw new IllegalArgumentException("HttpServletResponse is required for CookieLocalesResolver");
        }
        ResponseCookie cookie;
        if (locale == null) {
            request.removeAttribute(ATTRIBUTE_NAME_SET);
            request.setAttribute(ATTRIBUTE_NAME_UNSET, true);
            cookie = this.cookie.mutate().maxAge(0).build();
        } else {
            request.removeAttribute(ATTRIBUTE_NAME_UNSET);
            request.setAttribute(ATTRIBUTE_NAME_SET, locale);
            cookie = this.cookie.mutate().value(locale.toString()).build();
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        request.removeAttribute(ATTRIBUTE_NAME_CACHE);
    }

    @SuppressWarnings("JdkObsolete")
    private Locale[] loadFromCacheOrResolveLocales(HttpServletRequest request) {
        Locale[] cachedLocales = (Locale[]) request.getAttribute(ATTRIBUTE_NAME_CACHE);
        if (cachedLocales == null) {
            // Avoid duplicate entries but preserve order
            LinkedHashSet<Locale> locales = new LinkedHashSet<>();

            // Add locale from cookie (if any)
            getLocaleFromCookie(request).ifPresent(locales::add);

            // Add locales from the Accept-Language HTTP request header
            for (Enumeration<Locale> e = request.getLocales(); e.hasMoreElements(); ) {
                locales.add(e.nextElement());
            }

            // Add default locale as ultimate fallback
            locales.addAll(defaultLocales);

            cachedLocales = locales.toArray(new Locale[0]);
            request.setAttribute(ATTRIBUTE_NAME_CACHE, cachedLocales);
            log.debug("resolved locales: {}", (Object) cachedLocales);
        }
        return cachedLocales;
    }

    private Optional<Locale> getLocaleFromCookie(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE_NAME_UNSET) != null
            // setLocale(null) has been called on this request
            ? Optional.empty()
            // use the locale from setLocale(x), if any
            : Optional.ofNullable((Locale) request.getAttribute(ATTRIBUTE_NAME_SET))
            // otherwise lookup the cookie
            .or(() -> Optional.ofNullable(WebUtils.getCookie(request, cookie.getName()))
                .map(cookie -> {
                    try {
                        return StringUtils.parseLocale(cookie.getValue());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }));
    }
}
