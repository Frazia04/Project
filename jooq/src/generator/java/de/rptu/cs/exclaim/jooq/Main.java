package de.rptu.cs.exclaim.jooq;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.GeneratedSerialVersionUID;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Logging;
import org.jooq.meta.jaxb.Strategy;
import org.jooq.meta.jaxb.Target;
import org.jooq.tools.jdbc.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;

/**
 * Main class that is invoked by the :jooq:generateJooq[Dialect] Gradle tasks.
 */
@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        // Load properties passed from Gradle build
        Properties properties = System.getProperties();
        boolean isFreshDatabase = Boolean.parseBoolean(properties.getProperty("exclaim.is_fresh_database"));
        String jdbcUrl = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_url"));
        String jdbcUsername = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_username"));
        String jdbcPassword = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_password"));
        String outputDirectory = Objects.requireNonNull(properties.getProperty("exclaim.output_directory"));

        // Establish a connection to the database
        log.info("Connecting to JDBC URL {}", jdbcUrl);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword)) {
            DataSource dataSource = new SingleConnectionDataSource(conn);

            // Detect the current database schema name
            String schema = conn.getSchema();
            if (schema == null || schema.isEmpty()) {
                throw new IllegalArgumentException("Could not determine the schema name");
            }
            log.info("Detected schema name {}", schema);

            // Run Flyway migrations
            log.info("Running migrations...");
            Flyway flyway = Flyway.configure()
                .locations("classpath:de/rptu/cs/exclaim/db/migration")
                .callbacks("de/rptu/cs/exclaim/db/callback")
                .dataSource(dataSource)
                .schemas(schema)
                .cleanDisabled(false)
                .load();
            if (!isFreshDatabase) {
                flyway.clean();
            }
            flyway.migrate();

            // Run the jOOQ code generator
            log.info("Running jOOQ code generator...");
            GenerationTool jooq = new GenerationTool();
            jooq.setDataSource(dataSource);
            jooq.run(new Configuration()
                .withLogging(Logging.WARN)
                .withGenerator(new Generator()
                    .withName(CustomJavaGenerator.class.getName())
                    .withStrategy(new Strategy().withName(CustomGeneratorStrategy.class.getName()))
                    .withDatabase(new Database()
                        .withInputSchema(schema)
                        .withExcludes(flyway.getConfiguration().getTable())
                        .withOutputCatalogToDefault(true)
                        .withOutputSchemaToDefault(true)
                    )
                    .withTarget(new Target()
                        .withDirectory(outputDirectory)
                        .withPackageName("de.rptu.cs.exclaim.schema") // Overridden in CustomGeneratorStrategy.getJavaPackageName for some elements
                        .withLocale("en") // do not depend on Locale.getDefault(), empty string would be ignored
                        .withClean(false) // cleaning is done by Gradle
                    )
                    .withGenerate(new Generate()
                        .withPojos(true)
                        .withPojosEqualsAndHashCode(true)
                        .withImmutablePojos(true)
                        .withInterfaces(true)
                        .withImmutableInterfaces(true)
                        .withGeneratedSerialVersionUID(GeneratedSerialVersionUID.OFF)
                        .withGlobalTableReferences(false)
                    )
                )
            );
            log.info("Code generation completed.");
        }
    }
}
