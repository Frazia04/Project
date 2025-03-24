package de.rptu.cs.exclaim.i18n;

import de.rptu.cs.exclaim.ExclaimProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class I18nConfiguration implements WebMvcConfigurer {
    private static final String BASE_NAME = "messages";
    private final ExclaimProperties exclaimProperties;
    private final ServerProperties serverProperties;

    /**
     * Adding <code>?lang=de</code> or <code>?lang=en</code> to the request URL sets the locale cookie.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        registry.addInterceptor(lci);
    }

    /**
     * Use {@link CookieLocalesResolver} to manage the locale for HTTP requests.
     * <p>
     * First precedence is the locale stored in the cookie.
     * Then the locales in the Accept-Language HTTP request header are considered.
     * Ultimate fallback are the locales specified in the exclaim.i18n.default-locales property.
     */
    @Bean
    public CookieLocalesResolver localeResolver() {
        ExclaimProperties.I18n i18n = exclaimProperties.getI18n();
        ExclaimProperties.I18n.Cookie cookie = i18n.getCookie();
        return new CookieLocalesResolver(
            i18n.getDefaultLocales(),
            ResponseCookie
                .from(cookie.getName())
                .domain(cookie.getDomain())
                .path(Objects.toString(
                    cookie.getPath(),
                    StringUtils.defaultIfEmpty(serverProperties.getServlet().getContextPath(), "/")
                ))
                .httpOnly(cookie.getHttpOnly())
                .secure(cookie.getSecure())
                .maxAge(cookie.getMaxAge())
                .build()
        );
    }

    /**
     * Use {@link UserLocalesResolver} to manage the locale for when not within the context of an HTTP request.
     * <p>
     * First precedence is the locale saved for the given user.
     * Fallback are the locales specified in the exclaim.i18n.default-locales property.
     */
    @Bean
    public UserLocalesResolver userLocaleResolver() {
        return new UserLocalesResolver(exclaimProperties.getI18n().getDefaultLocales());
    }

    @Bean
    public ICUMessageSource messageSource(MultipleLocalesResolver multipleLocalesResolver) {
        return new ICUMessageSourceImpl(BASE_NAME, exclaimProperties.getTimezone(), multipleLocalesResolver);
    }

    @Bean
    public ICUMessageSourceAccessor msg(
        ICUMessageSource icuMessageSource,
        MultipleLocalesResolver multipleLocalesResolver,
        UserLocalesResolver userLocalesResolver,
        @Value("classpath*:" + BASE_NAME + "_*.properties") Resource[] resources) {
        // We need to collect the supported languages as map from language key to language name.
        // The map shall be sorted by language name. In a first step, we create a list of pairs.
        List<Tuple2<String, String>> languagesList = new ArrayList<>(resources.length);
        int prefixLength = BASE_NAME.length() + 1;
        int suffixLength = ".properties".length();
        for (Resource resource : resources) {
            String fileName = Objects.requireNonNull(resource.getFilename());
            String key = fileName.substring(prefixLength, fileName.length() - suffixLength);
            Locale locale = Locale.forLanguageTag(key);
            String languageName = locale.getDisplayName(locale);
            languagesList.add(new Tuple2<>(key, languageName));
        }

        // Sort the list by language name
        languagesList.sort(Comparator.comparing(Tuple2::v2));

        // Create the actual map (LinkedHashMap to preserve order)
        Map<String, String> languagesMap = new LinkedHashMap<>();
        for (Tuple2<String, String> tuple2 : languagesList) {
            languagesMap.put(tuple2.v1, tuple2.v2);
        }
        languagesMap = Collections.unmodifiableMap(languagesMap); // read-only

        return new ICUMessageSourceAccessor(icuMessageSource, multipleLocalesResolver, userLocalesResolver, languagesMap);
    }
}
