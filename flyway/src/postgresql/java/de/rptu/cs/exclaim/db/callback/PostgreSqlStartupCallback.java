package de.rptu.cs.exclaim.db.callback;

/**
 * Callback to be run when starting Flyway, before doing any validation or migration.
 */
public class PostgreSqlStartupCallback extends AbstractStartupCallback {
    // Use defaults from AbstractStartupCallback, nothing to add for PostgreSQL.
}
