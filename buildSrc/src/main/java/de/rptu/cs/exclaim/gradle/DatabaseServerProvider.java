package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.process.JavaForkOptions;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.rptu.cs.exclaim.gradle.TestcontainersUtils.addTestcontainersContainerToTask;

/**
 * Database Server Provider
 * <p>
 * Some tasks need a database to interact with. The {@link #applyTo(Project, TaskProvider, SqlDialect)} method in this
 * class adds system properties to the task containing the JDBC connection parameters (url, username, password).
 * <p>
 * For H2, we simply use an embedded in-memory database. For other dialects, we check whether connection parameters have
 * been provided to the build using environment variables, otherwise we start a Docker container with the database
 * server in a doFirst action of the task and stop it after the task has completed.
 */
public class DatabaseServerProvider {
    public static class DatabaseServerConfiguration {
        // Container class from Testcontainers
        public final Class<? extends JdbcDatabaseContainer<?>> containerClass;

        // Docker image to use
        public final String image;

        // Names of environment variables that can be provided to the build
        public final String urlVariableName;
        public final String usernameVariableName;
        public final String passwordVariableName;

        // The constructor needs to cast the raw JdbcDatabaseContainer type to generic JdbcDatabaseContainer<?>
        @SuppressWarnings({"rawtypes", "unchecked"})
        private DatabaseServerConfiguration(Class<? extends JdbcDatabaseContainer> containerClass, String image, String urlVariableName, String usernameVariableName, String passwordVariableName) {
            this.containerClass = (Class) containerClass;
            this.image = image;
            this.urlVariableName = urlVariableName;
            this.usernameVariableName = usernameVariableName;
            this.passwordVariableName = passwordVariableName;
        }
    }

    public static final Map<SqlDialect, DatabaseServerConfiguration> DATABASES = Map.of(
        SqlDialect.PostgreSql, new DatabaseServerConfiguration(
            PostgreSQLContainer.class,
            PostgreSQLContainer.IMAGE,
            "POSTGRES_URL",
            "POSTGRES_USER",
            "POSTGRES_PASSWORD"
        )
    );

    // Names of the system properties provided to the task
    private static final String JDBC_URL_PROPERTY_NAME = "exclaim.jdbc_url";
    private static final String JDBC_USERNAME_PROPERTY_NAME = "exclaim.jdbc_username";
    private static final String JDBC_PASSWORD_PROPERTY_NAME = "exclaim.jdbc_password";
    private static final String IS_FRESH_DATABASE_PROPERTY_NAME = "exclaim.is_fresh_database";

    /**
     * Provide system properties with JDBC connection parameters to a task.
     *
     * @param project      the project which the task is in
     * @param taskProvider the task provider
     * @param dialect      SQL dialect of the requested database
     * @param <TASK>       type of the Gradle task
     */
    public static <TASK extends Task & JavaForkOptions> void applyTo(Project project, TaskProvider<TASK> taskProvider, SqlDialect dialect) {
        if (dialect == SqlDialect.H2) {
            // Use an H2 in-memory database
            taskProvider.configure(task -> {
                task.getLogger().info("Task '{}' will use an H2 in-memory database", task.getPath());
                task.getSystemProperties().putAll(Map.of(
                    // Use a named database and DB_CLOSE_DELAY=-1 such that it lives as long as the executed Java task
                    JDBC_URL_PROPERTY_NAME, "jdbc:h2:mem:" + task.getPath().replace(":", "_") + ";DB_CLOSE_DELAY=-1",
                    JDBC_USERNAME_PROPERTY_NAME, "sa",
                    JDBC_PASSWORD_PROPERTY_NAME, ""
                ));
            });
        } else {
            DatabaseServerConfiguration conf = Objects.requireNonNull(DATABASES.get(dialect));
            Map<String, String> env = System.getenv();
            String jdbcUrl = env.get(conf.urlVariableName);
            if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
                // Use external database server. We register a dummy build service serving as mutex, such that tasks
                // using the same external database server cannot run in parallel.
                Provider<DummyBuildService> buildServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("External" + dialect.name() + "Database", DummyBuildService.class, spec ->
                    spec.getMaxParallelUsages().set(1)
                );
                taskProvider.configure(task -> {
                    task.getLogger().info("Task '{}' will use an external database server at {}", task.getPath(), jdbcUrl);
                    task.getSystemProperties().putAll(Map.of(
                        JDBC_URL_PROPERTY_NAME, jdbcUrl,
                        JDBC_USERNAME_PROPERTY_NAME, env.getOrDefault(conf.usernameVariableName, ""),
                        JDBC_PASSWORD_PROPERTY_NAME, env.getOrDefault(conf.passwordVariableName, "")
                    ));
                    task.usesService(buildServiceProvider);
                });
            } else {
                // Use database server managed with Testcontainers
                taskProvider.configure(task -> task.getLogger().info("Task '{}' will use a database server managed with Testcontainers", task.getPath()));
                DockerImageName dockerImageName = DockerImageName.parse(conf.image).withTag((String) Objects.requireNonNull(project.getExtensions().getExtraProperties().get(dialect.name().toLowerCase(Locale.ROOT) + "_docker_tag")));
                // noinspection Convert2Lambda (cannot use lambda expressions here due to limitation by Gradle)
                addTestcontainersContainerToTask(project, taskProvider, "database", conf.containerClass, dockerImageName.asCanonicalNameString(), new Consumer<TestcontainersUtils.CallbackParameters<TASK, JdbcDatabaseContainer<?>>>() {
                    @Override
                    public void accept(TestcontainersUtils.CallbackParameters<TASK, JdbcDatabaseContainer<?>> parameters) {
                        parameters.task.getSystemProperties().putAll(Map.of(
                            JDBC_URL_PROPERTY_NAME, parameters.container.getJdbcUrl(),
                            JDBC_USERNAME_PROPERTY_NAME, parameters.container.getUsername(),
                            JDBC_PASSWORD_PROPERTY_NAME, parameters.container.getPassword(),
                            IS_FRESH_DATABASE_PROPERTY_NAME, parameters.isFirstUse
                        ));
                    }
                });
            }
        }
    }
}
