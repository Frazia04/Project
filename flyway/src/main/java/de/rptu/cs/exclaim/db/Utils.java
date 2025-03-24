package de.rptu.cs.exclaim.db;

import org.flywaydb.core.api.configuration.Configuration;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

public class Utils {
    public static final Settings JOOQ_SETTINGS = new Settings()
        // NOTE: Keep in sync with app/src/main/java/de.rptu.cs.exclaim.DatabaseConfiguration.jooqConfigurationCustomizer()

        // Make sure that we do not depend on Locale.getDefault()
        .withLocale(Locale.ROOT)

        // We prefer to have identifiers lower-case and keywords upper-case
        .withRenderNameCase(RenderNameCase.LOWER_IF_UNQUOTED)
        .withRenderKeywordCase(RenderKeywordCase.UPPER)

        // Use unqualified table names (we have only one schema)
        .withRenderCatalog(false)
        .withRenderSchema(false);

    public static String getSchema(org.flywaydb.core.api.callback.Context context) {
        return getSchema(context.getConfiguration(), context.getConnection());
    }

    public static String getSchema(org.flywaydb.core.api.migration.Context context) {
        return getSchema(context.getConfiguration(), context.getConnection());
    }

    private static String getSchema(Configuration configuration, Connection connection) {
        String schema = configuration.getDefaultSchema();
        if (schema != null) {
            return schema;
        }
        String[] schemas = configuration.getSchemas();
        if (schemas.length > 0) {
            return schemas[0];
        }
        try {
            return Objects.requireNonNull(connection.getSchema(), "Could not detect the schema");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not detect the schema", e);
        }
    }
}
