package de.rptu.cs.exclaim;

import lombok.extern.slf4j.Slf4j;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderNameCase;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class DatabaseConfiguration {
    /**
     * Configuration for Flyway.
     *
     * @see org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
     */
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> configuration
            .locations("classpath:de/rptu/cs/exclaim/db/migration")
            .callbacks("de/rptu/cs/exclaim/db/callback");
    }

    /**
     * Configuration for jOOQ.
     *
     * @see org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
     */
    @Bean
    public DefaultConfigurationCustomizer jooqConfigurationCustomizer() {
        return configuration -> configuration.set(configuration.settings()
            // NOTE: Keep in sync with flyway/src/main/java/de.rptu.cs.exclaim.db.Utils.JOOQ_SETTINGS

            // Make sure that we do not depend on Locale.getDefault()
            .withLocale(Locale.ROOT)

            // We prefer to have identifiers lower-case and keywords upper-case
            .withRenderNameCase(RenderNameCase.LOWER_IF_UNQUOTED)
            .withRenderKeywordCase(RenderKeywordCase.UPPER)

            // Use unqualified table names (we have only one schema)
            .withRenderCatalog(false)
            .withRenderSchema(false)
        );
    }
}
