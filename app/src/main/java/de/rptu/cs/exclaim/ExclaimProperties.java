package de.rptu.cs.exclaim;

import de.rptu.cs.exclaim.validation.SpELAssert;
import de.rptu.cs.exclaim.validation.ValidSpELExpression;
import jakarta.validation.constraints.Min;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "exclaim")
@Validated
@Getter
@ToString
@EqualsAndHashCode
public class ExclaimProperties {
    public ExclaimProperties(
        @DefaultValue BackgroundJobs backgroundJobs,
        @DefaultValue I18n i18n,
        @DefaultValue Metrics metrics,
        @DefaultValue Rte rte,
        @DefaultValue Validation validation,
        @DefaultValue("exclaim@cs.uni-kl.de") String adminContact,
        @DefaultValue("exclaim@cs.uni-kl.de") String emailSender,
        @DefaultValue("http://localhost:8080") String publicUrl,
        @Nullable ZoneId timezone,
        @DefaultValue("false") boolean bypassNewUserActivation,
        @DefaultValue("1d") Duration passwordResetEmailValidity,
        @DefaultValue("") String docsPassword
    ) {
        this.backgroundJobs = backgroundJobs;
        this.i18n = i18n;
        this.metrics = metrics;
        this.rte = rte;
        this.validation = validation;
        this.adminContact = adminContact;
        this.emailSender = emailSender;
        this.publicUrl = publicUrl.replaceFirst("/$", "");
        this.timezone = timezone != null ? timezone : ZoneId.systemDefault();
        this.bypassNewUserActivation = bypassNewUserActivation;
        this.passwordResetEmailValidity = passwordResetEmailValidity;
        this.docsPassword = docsPassword;
    }

    private final BackgroundJobs backgroundJobs;

    private final I18n i18n;

    private final Metrics metrics;

    @SpELAssert(
        value = "!enabled || (url?.trim() > '' && apiKey?.trim() > '')",
        message = "Setting .enabled to true also requires to set .url and .api-key")
    private final Rte rte;

    private final Validation validation;

    /**
     * Contact information of the system admin.
     */
    private final String adminContact;

    /**
     * Sender address for e-mails.
     */
    private final String emailSender;

    /**
     * Public URL to the application (used to generate URLs in e-mail messages).
     */
    private final String publicUrl;

    /**
     * Time zone to use for displaying date and time values. Defaults to the system's time zone.
     */
    private final ZoneId timezone;

    /**
     * Whether newly registered users are activated without sending an activation email.
     */
    private final boolean bypassNewUserActivation;

    /**
     * How long the link in a password reset email remains valid.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private final Duration passwordResetEmailValidity;

    /**
     * Password to access the documentation, bcrypt encoded. Username is always "docs".
     */
    private final String docsPassword;

    @Getter
    @ToString
    @EqualsAndHashCode
    public static class BackgroundJobs {
        public BackgroundJobs(
            @DefaultValue("15s") Duration pollInterval,
            @DefaultValue("10s") Duration shutdownTimeout
        ) {
            this.pollInterval = pollInterval;
            this.shutdownTimeout = shutdownTimeout;
        }

        /**
         * The time to wait between polls for due background jobs.
         */
        @DurationUnit(ChronoUnit.SECONDS)
        private final Duration pollInterval;

        /**
         * The timeout for the background job scheduler to shut down.
         */
        @DurationUnit(ChronoUnit.SECONDS)
        private final Duration shutdownTimeout;
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    public static class I18n {
        public I18n(
            @DefaultValue({"en", "de"}) List<Locale> defaultLocales,
            @DefaultValue Cookie cookie
        ) {
            this.defaultLocales = defaultLocales;
            this.cookie = cookie;
        }

        /**
         * Default locales to use when no supported locale can be determined.
         * The list is sorted by precedence, thus falling back to later entries if an entry cannot be found.
         */
        private final List<Locale> defaultLocales;

        private final Cookie cookie;

        @Getter
        @ToString
        @EqualsAndHashCode
        public static class Cookie {
            public Cookie(
                @DefaultValue("exclaim-locale") String name,
                @Nullable String domain,
                @Nullable String path,
                @DefaultValue("true") boolean httpOnly,
                @DefaultValue("false") boolean secure,
                @DefaultValue("365d") Duration maxAge
            ) {
                this.name = name;
                this.domain = domain;
                this.path = path;
                this.httpOnly = httpOnly;
                this.secure = secure;
                this.maxAge = maxAge;
            }

            /**
             * Name of the cookie holding the users preferred locale.
             */
            private final String name;

            /**
             * Domain of the cookie holding the users preferred locale.
             */
            @Nullable
            private final String domain;

            /**
             * Path of the cookie holding the users preferred locale. Defaults to the servlet context path.
             */
            @Nullable
            private final String path;

            /**
             * Whether to use "HttpOnly" cookies for the users preferred locale.
             */
            private final boolean httpOnly;

            /**
             * Whether to always mark the cookie holding the users preferred locale as secure.
             */
            private final boolean secure;

            /**
             * Maximum age of the cooking holding the users preferred locale. If a duration suffix is not specified, seconds will be used.
             */
            @DurationUnit(ChronoUnit.SECONDS)
            private final Duration maxAge;
        }
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Metrics {
        public Metrics(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("hasIpAddress('127.0.0.0/8') or hasIpAddress('::1')") String access
        ) {
            this.enabled = enabled;
            this.access = access;
        }

        /**
         * Whether metrics should be collected
         */
        private final boolean enabled;

        /**
         * A Spring Security expression for the access restriction on the metrics endpoint.
         * See <a href="https://docs.spring.io/spring-security/reference/6.0.0/servlet/authorization/expression-based.html#el-common-built-in">Spring Security documentation on expressions</a>
         */
        @ValidSpELExpression
        private final String access;
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Rte {
        public Rte(
            @DefaultValue("false") boolean enabled,
            @Nullable String url,
            @Nullable String apiKey,
            @DefaultValue("5") short maxParallel
        ) {
            this.enabled = enabled;
            this.url = url;
            this.apiKey = apiKey;
            this.maxParallel = maxParallel;
        }

        /**
         * Whether Exclaim should use an RTE (Remote Test Executor)
         */
        private final boolean enabled;

        /**
         * The URL where RTE is running
         */
        @Nullable
        private final String url;

        /**
         * The API key to authenticate against the RTE service
         */
        @Nullable
        private final String apiKey;

        /**
         * The maximum number of test jobs running in parallel. Has no effect if greater than
         * {@code spring.task.execution.pool.core-size}.
         */
        @Min(value = 1, message = "max parallel jobs must be >= 1")
        private final short maxParallel;
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Validation {
        public Validation(@DefaultValue("[0-9]{6}") Pattern studentIdRegex) {
            this.studentIdRegex = studentIdRegex;
        }

        /**
         * Regular expression for valid student ids
         */
        private final Pattern studentIdRegex;
    }
}
