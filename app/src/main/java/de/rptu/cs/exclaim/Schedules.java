package de.rptu.cs.exclaim;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static de.rptu.cs.exclaim.schema.tables.PasswordResets.PASSWORD_RESETS;

/**
 * Recurring background tasks.
 * <p>
 * The tasks are executed in the taskScheduler bean created by Spring Boot.
 * The scheduler can be configured using the spring.task.scheduling properties.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class Schedules {
    private final DSLContext ctx;

    /**
     * Clean up expired password reset tokens from the database.
     * <p>
     * Runs 30 seconds after application startup, then every 12 hours.
     */
    @Scheduled(initialDelay = 30, fixedDelay = 43200, timeUnit = TimeUnit.SECONDS)
    public void cleanupExpiredPasswordResetTokens() {
        log.debug("Deleting expired password reset tokens...");
        int count = ctx
            .deleteFrom(PASSWORD_RESETS)
            .where(PASSWORD_RESETS.VALID_UNTIL.le(LocalDateTime.now(ZoneOffset.UTC)))
            .execute();
        if (count > 0) {
            log.info("Deleted {} expired password reset tokens.", count);
        } else {
            log.debug("There are no expired password reset tokens.");
        }
    }
}
