package de.rptu.cs.exclaim.db.callback;

import de.rptu.cs.exclaim.db.FixOldMigrations;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import static de.rptu.cs.exclaim.db.Utils.JOOQ_SETTINGS;

/**
 * Callback to be run when starting Flyway, before doing any validation or migration.
 */
@Slf4j
public abstract class AbstractStartupCallback extends BaseCallback {
    @Override
    public boolean supports(Event event, Context context) {
        // Run before validation (if validation is enabled), otherwise run before migrations.
        return event == Event.BEFORE_VALIDATE || event == Event.BEFORE_MIGRATE;
    }

    @Override
    public void handle(Event event, Context context) {
        if (event == Event.BEFORE_VALIDATE
            || (event == Event.BEFORE_MIGRATE && !context.getConfiguration().isValidateOnMigrate())
        ) {
            log.debug("Running startup callback in {} hook", event);
            execute(context, DSL.using(context.getConnection(), JOOQ_SETTINGS));
        } else {
            log.debug("Not running startup callback in {} hook", event);
        }
    }

    protected void execute(Context context, DSLContext ctx) {
        new FixOldMigrations(context, ctx).fixOldMigrations();
    }
}
