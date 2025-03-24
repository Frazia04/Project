package de.rptu.cs.exclaim.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;

import java.util.regex.Pattern;

/**
 * Apply custom changes to the logback configuration:
 * <ul>
 * <li>Lower the log level of Flyways message for higher than supported database version
 * </ul>
 */
@Order(LoggingApplicationListener.DEFAULT_ORDER + 1)
public class LogbackConfigurer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            Pattern flywayUpgradePattern = Pattern.compile("Flyway upgrade recommended: H2 [\\d.]+ is newer than this version of Flyway and support has not been tested. The latest supported version of H2 is [\\d.]+.");
            context.addTurboFilter(new TurboFilter() {
                @Override
                public FilterReply decide(
                    @Nullable Marker marker,
                    Logger logger,
                    Level level,
                    @Nullable String format,
                    @Nullable Object[] params,
                    @Nullable Throwable t
                ) {
                    if (level == Level.WARN && format != null && flywayUpgradePattern.matcher(format).matches()) {
                        logger.debug(format);
                        return FilterReply.DENY;
                    }
                    return FilterReply.NEUTRAL;
                }
            });
        }
    }
}
