package de.rptu.cs.exclaim.db.callback;

import de.rptu.cs.exclaim.db.InitializeDatabase;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Context;
import org.jooq.DSLContext;

/**
 * Callback to be run when starting Flyway, before doing any validation or migration.
 */
@Slf4j
public class H2StartupCallback extends AbstractStartupCallback {
    @Override
    protected void execute(Context context, DSLContext ctx) {
        if (InitializeDatabase.initialize(context, ctx)) {
            log.debug("Database initialization successful, no need to fix any migrations.");
        } else {
            super.execute(context, ctx);
        }
    }
}
