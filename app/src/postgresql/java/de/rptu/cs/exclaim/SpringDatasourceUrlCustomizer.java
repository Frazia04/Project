package de.rptu.cs.exclaim;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Configuration that adjusts the <code>spring.datasource.url</code> property.
 * <p>
 * When using PostgreSQL, we by default set the <code>autosave=always</code> connection parameter such that transactions
 * can continue after a statement has failed (roll back only the failed statement instead of the whole transaction).
 * This produces the same behaviour as H2 and is necessary such that controllers can handle a DataAccessException and
 * still continue with the transaction. Without handling the exception in the controller, the transaction will be rolled
 * back if the controller method is annotated <code>@Transactional</code>.
 * <p>
 * This initializer is registered in the <code>META-INF/spring.factories</code> file.
 */
@Slf4j
public class SpringDatasourceUrlCustomizer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getProperty(DATASOURCE_URL_PROPERTY);
        if (url != null && url.startsWith("jdbc:postgresql:")) {
            String originalUrl = url;
            if (!url.matches("(?i)[?&]autosave=")) {
                url += (url.contains("?") ? "&" : "?") + "autosave=always";
                if (!url.matches("(?i)[?&]cleanupSavepoints=")) {
                    url += "&cleanupSavepoints=true";
                }
            }
            if (!url.equals(originalUrl)) {
                log.info("Adjusting {} property from {} to {}", DATASOURCE_URL_PROPERTY, originalUrl, url);
                environment.getPropertySources().addFirst(new MapPropertySource(
                    "SpringDatasourceUrlCustomizer",
                    Map.of("spring.datasource.url", url)
                ));
                ConfigurationPropertySources.attach(environment);
            }
        }
    }
}
