package de.rptu.cs.exclaim.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Main class that is invoked by the :flyway:condenseH2Migrations Gradle task.
 */
@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        // Load property passed from Gradle build
        String outputFile = Objects.requireNonNull(System.getProperty("exclaim.output_file"));

        // Create the H2 database file (not in-memory, because the file is useful for IDE tooling)
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:", "sa", "")) {
            DataSource dataSource = new SingleConnectionDataSource(conn, true);

            // Run Flyway migrations
            log.info("Running migrations...");
            Flyway.configure()
                .locations("classpath:de/rptu/cs/exclaim/db/migration")
                .callbacks("de/rptu/cs/exclaim/db/callback")
                .dataSource(dataSource)
                .schemas("PUBLIC") // DEFAULT_SCHEMA_NAME in InitializeDatabase.java
                .table("flyway_schema_history") // DEFAULT_FLYWAY_TABLE_NAME in InitializeDatabase.java
                .load()
                .migrate();

            // Dump database to an SQL script
            log.info("Dumping database to {}", outputFile);
            try (PreparedStatement stmt = conn.prepareStatement("SCRIPT NOPASSWORDS NOSETTINGS TO ? CHARSET 'UTF-8'")) {
                stmt.setString(1, outputFile);
                stmt.execute();
            }

            // Delete the CREATE USER statement
            log.info("Post-processing dump...");
            deleteCreateUserStatementFromFile(outputFile);
            log.info("Database dump completed.");
        }
    }

    private static void deleteCreateUserStatementFromFile(String fileName) throws IOException {
        // Stream the input file into a temporary file, thereby deleting the CREATE USER statement
        File inputFile = new File(fileName);
        File outputFile = new File(fileName + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))
        ) {
            Pattern createUserStatement = Pattern.compile("CREATE\\s+USER\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:'[^']*'|\"[^\"]*\")(?:\\s+PASSWORD\\s+(?:'[^']*'|\"[^\"]*\"))?(?:\\s+ADMIN)?\\s*;\\s*");
            String line;
            while ((line = reader.readLine()) != null) {
                String replacedLine = createUserStatement.matcher(line).replaceAll("");
                if (!replacedLine.isEmpty() || line.isEmpty()) {
                    writer.write(replacedLine);
                    writer.write('\n');
                }
            }
        }

        // Replace the original file with our temporary file.
        // Must delete original first due to rename semantics on Windows.
        if (!(inputFile.delete() && outputFile.renameTo(inputFile))) {
            throw new IOException("Could not rename " + outputFile + " to " + inputFile);
        }
    }
}
