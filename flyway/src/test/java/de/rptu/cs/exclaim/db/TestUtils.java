package de.rptu.cs.exclaim.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;

public class TestUtils {
    // Load properties passed from Gradle build
    public static final String JDBC_URL = Objects.requireNonNull(System.getProperty("exclaim.jdbc_url"));
    public static final String JDBC_USERNAME = Objects.requireNonNull(System.getProperty("exclaim.jdbc_username"));
    public static final String JDBC_PASSWORD = Objects.requireNonNull(System.getProperty("exclaim.jdbc_password"));

    static {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
    }

    public static void testFlyway(ThrowingBiConsumer<DataSource, FluentConfiguration> consumer) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USERNAME, JDBC_PASSWORD)) {
            DataSource dataSource = new SingleConnectionDataSource(conn);
            FluentConfiguration flyway = Flyway.configure()
                .locations("classpath:de/rptu/cs/exclaim/db/migration")
                .callbacks("de/rptu/cs/exclaim/db/callback")
                .dataSource(dataSource)
                .cleanDisabled(false);
            flyway.load().clean();
            consumer.accept(dataSource, flyway);
        }
    }

    public static void testMigration(String oldVersion, String newVersion, ThrowingConsumer<DSLContext> runInOldVersion, ThrowingConsumer<DSLContext> runInNewVersion) throws Exception {
        testFlyway((dataSource, flyway) -> {
            flyway.target(oldVersion).load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            runInOldVersion.accept(ctx);
            flyway.target(newVersion).load().migrate();
            runInNewVersion.accept(ctx);
        });
    }

    //------------------------------------------------------------------------------------------------------------------
    // Variants of java.util.function.Consumer and java.util.function.BiConsumer that can throw any Exception

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T var1) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> {
        void accept(T var1, U var2) throws Exception;
    }
}
