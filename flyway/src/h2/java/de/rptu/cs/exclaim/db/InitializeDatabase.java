package de.rptu.cs.exclaim.db;

import de.rptu.cs.exclaim.db.callback.H2StartupCallback;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.configuration.Configuration;
import org.jooq.DSLContext;

import java.net.URL;

/**
 * If the current database is a fresh H2 database, and we are using default parameters, then we do not run migrations.
 * Instead, we import the bundled database dump that was created at build time (:flyway:condenseH2Migrations).
 * <p>
 * The goal of this approach is to save time when deploying the application or using it for testing. Over time, there
 * will be many migrations altering existing tables. The single script directly creates the final result without all the
 * intermediate steps. We do not care about other database systems: Quick demo or testing installations use H2, others
 * can just run all migrations.
 * <p>
 * This class is used by {@link H2StartupCallback}.
 */
@Slf4j
public class InitializeDatabase {
    private static final String DEFAULT_SCHEMA_NAME = "PUBLIC";
    private static final String DEFAULT_FLYWAY_TABLE_NAME = "flyway_schema_history";
    private static final String INIT_SCRIPT_PATH = "de/rptu/cs/exclaim/db/exclaim-schema.sql";

    /**
     * Initialize the database (instead of running the migrations) if all the following holds:
     * <ul>
     * <li>We are connected to an H2 database
     * <li>Flyway settings (table, schema) are default
     * <li>The default database schema name is used
     * <li>There are no existing tables
     * <li>The initialization script is found on the classpath
     * </ul>
     *
     * @return Whether the database has been initialized using the initialization script.
     */
    public static boolean initialize(Context context, DSLContext ctx) {
        // Check that we are using the default schema and table name without a specific migration target
        Configuration configuration = context.getConfiguration();
        if (configuration.getTarget() == null
            && DEFAULT_FLYWAY_TABLE_NAME.equals(configuration.getTable())
            && DEFAULT_SCHEMA_NAME.equals(Utils.getSchema(context))
        ) {
            log.debug("Default schema/table settings without target detected");

            // Check that there are no existing tables
            boolean exists = ctx
                // We can use H2 vendor-specific syntax here because this class is only for H2
                .fetchSingle("SELECT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?)", DEFAULT_SCHEMA_NAME)
                .get(0, boolean.class);
            if (!exists) {
                log.debug("Database is empty");

                // Check that we have the initialization script available. This is not the case when running the
                // :flyway:condenseH2Migrations Gradle task where we create that file.
                URL url = InitializeDatabase.class.getClassLoader().getResource(INIT_SCRIPT_PATH);
                if (url != null) {
                    log.debug("Initialization script found at {}", url);

                    // Run the initialization script
                    log.info("Detected fresh H2 database with default settings, running initialization script instead of migrations.");
                    ctx.execute("RUNSCRIPT FROM ? CHARSET 'UTF-8'", "classpath:/" + INIT_SCRIPT_PATH);
                    return true;
                } else {
                    log.debug("Initialization script not found");
                }
            } else {
                log.debug("Existing tables detected");
            }
        } else {
            log.debug("Non-default schema/table settings or specific target detected");
        }
        return false;
    }
}
