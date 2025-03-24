package de.rptu.cs.exclaim;

import de.rptu.cs.exclaim.data.interfaces.IExercise;
import de.rptu.cs.exclaim.i18n.ICUMessageSourceAccessor;
import de.rptu.cs.exclaim.schema.enums.Term;
import de.rptu.cs.exclaim.schema.enums.Weekday;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.spring6.ISpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static de.rptu.cs.exclaim.utils.UploadManager.INTERNAL_DTF;

/**
 * Configuration for the Thymeleaf Template Engine.
 * <p>
 * We need to adapt the default Spring Boot auto-configuration in order to access our custom i18n classes.
 *
 * @see org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ThymeleafConfiguration {
    private final ThymeleafProperties thymeleafProperties;

    /**
     * Custom TemplateEngine to replace the default {@link org.thymeleaf.spring6.SpringTemplateEngine}.
     */
    static class ICUMessageSourceTemplateEngine extends TemplateEngine implements ISpringTemplateEngine {
        @Override
        public void setTemplateEngineMessageSource(MessageSource templateEngineMessageSource) {
            // ignored
        }
    }

    @Bean
    public ICUMessageSourceTemplateEngine templateEngine(
        ObjectProvider<ITemplateResolver> templateResolvers,
        ObjectProvider<IDialect> dialects,
        ICUMessageSourceAccessor msg) {

        // We cannot use the SpringTemplateEngine, since it resets the message resolver on initialization.
        ICUMessageSourceTemplateEngine engine = new ICUMessageSourceTemplateEngine();

        // Set the SpringStandardDialect (the SpringTemplateEngine sets it in its constructor)
        SpringStandardDialect springStandardDialect = new SpringStandardDialect();
        springStandardDialect.setEnableSpringELCompiler(thymeleafProperties.isEnableSpringElCompiler());
        springStandardDialect.setRenderHiddenMarkersBeforeCheckboxes(thymeleafProperties.isRenderHiddenMarkersBeforeCheckboxes());
        engine.setDialect(springStandardDialect);

        // Other configuration copied from the auto configuration class
        templateResolvers.orderedStream().forEach(engine::addTemplateResolver);
        dialects.orderedStream().forEach(engine::addDialect);

        // Set our custom message resolver
        engine.setMessageResolver(new ICUMessageResolver(msg));
        return engine;
    }

    @Bean
    public ThymeleafViewResolver thymeleafViewResolver(ICUMessageSourceTemplateEngine engine) {
        // This is just a copy from the auto configuration class, but using
        // ICUMessageSourceTemplateEngine instead of SpringTemplateEngine.
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(engine);
        resolver.setCharacterEncoding(thymeleafProperties.getEncoding().name());
        resolver.setContentType(appendCharset(thymeleafProperties.getServlet().getContentType(), resolver.getCharacterEncoding()));
        resolver.setProducePartialOutputWhileProcessing(thymeleafProperties.getServlet().isProducePartialOutputWhileProcessing());
        resolver.setExcludedViewNames(thymeleafProperties.getExcludedViewNames());
        resolver.setViewNames(thymeleafProperties.getViewNames());
        resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 5);
        resolver.setCache(thymeleafProperties.isCache());
        return resolver;
    }

    private String appendCharset(MimeType type, String charset) {
        if (type.getCharset() != null) {
            return type.toString();
        }
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("charset", charset);
        parameters.putAll(type.getParameters());
        return new MimeType(type, parameters).toString();
    }

    @RequiredArgsConstructor
    private static class ICUMessageResolver implements IMessageResolver {
        private final ICUMessageSourceAccessor msg;

        @Override
        public String getName() {
            return "ICUMessageResolver";
        }

        @Override
        public Integer getOrder() {
            return 0;
        }

        @Override
        @Nullable
        public String resolveMessage(ITemplateContext context, Class<?> origin, String key, @Nullable Object[] messageParameters) {
            if (messageParameters != null && messageParameters.length == 1 && messageParameters[0] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) messageParameters[0];
                return msg.getMessage(key, map, (String) null);
            }
            return msg.getMessage(key, messageParameters, (String) null);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key, Object[] messageParameters) {
            return "??" + key + "??";
        }
    }

    @Component("format")
    @RequiredArgsConstructor
    public static class FormatUtils {
        private final ICUMessageSourceAccessor msg;
        private final NumberFormat nf = new DecimalFormat("##.#");
        private final DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
        private final DateTimeFormatter df = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

        public String points(@Nullable Number points) {
            return points == null ? "-" : nf.format(points);
        }

        public String dateTime(LocalDateTime dateTime) {
            return dtf.withLocale(msg.getBestSupportedLocale()).format(dateTime);
        }

        public String date(LocalDateTime date) {
            return df.withLocale(msg.getBestSupportedLocale()).format(date);
        }

        public String internalDateTime(LocalDateTime dateTime) {
            return INTERNAL_DTF.format(dateTime);
        }

        public String day(@Nullable Weekday weekday) {
            return weekday == null ? "-" : weekday.toDayOfWeek().getDisplayName(TextStyle.FULL, msg.getBestSupportedLocale());
        }

        public String term(Term t) {
            return msg.getMessage("common.term." + t.name().toLowerCase(Locale.ROOT));
        }

        public String term(IExercise e) {
            StringBuilder sb = new StringBuilder();
            Term term = e.getTerm();
            int year = e.getYear();
            if (term != null) {
                sb.append(term(term));
                sb.append(' ');
                sb.append(year);
                if (term == Term.WINTER) {
                    int nextYearShort = (year + 1) % 100;
                    sb.append("/").append(nextYearShort == 0
                        ? year + 1
                        : String.format("%02d", nextYearShort)
                    );
                }
            } else if (year != 0) {
                sb.append(year);
            }
            String comment = e.getTermComment();
            if (!comment.isEmpty()) {
                boolean hasYear = sb.length() != 0;
                if (hasYear) sb.append(" (");
                sb.append(comment);
                if (hasYear) sb.append(')');
            }
            return sb.toString();
        }

        public String programOutput(@Nullable String s) {
            return s == null ? "" : "\n" + s.replace('\u0000', '\u2400') + "\n";
        }

        public String translateMap(String code, String k1, String v1, String k2, String v2) {
            return msg.getMessage(code, Map.of(k1, v1, k2, v2));
        }
    }
}
