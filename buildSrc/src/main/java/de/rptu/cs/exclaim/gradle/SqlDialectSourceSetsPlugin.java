package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.Map;

import static de.rptu.cs.exclaim.gradle.SqlDialectPlugin.SQL_DIALECT_ATTRIBUTE;

/**
 * SQL Dialect Source Sets Plugin
 * <p>
 * This plugin adds a separate source set for each supported SQL dialect.
 */
public class SqlDialectSourceSetsPlugin implements Plugin<Project> {
    private static final Map<SqlDialect, String> SQL_DIALECT_DEPENDENCIES = Map.of(
        SqlDialect.H2, "com.h2database:h2",
        SqlDialect.PostgreSql, "org.postgresql:postgresql"
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        // Add a source set for each dialect
        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            sourceSets.register(dialect.getSourceSetName(), dialectSourceSet -> {
                // Inherit implementation and runtimeOnly configurations from source set main
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                project.getConfigurations().named(dialectSourceSet.getImplementationConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(mainSourceSet.getImplementationConfigurationName())
                ));
                project.getConfigurations().named(dialectSourceSet.getRuntimeOnlyConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(mainSourceSet.getRuntimeOnlyConfigurationName())
                ));

                // Add the dialect attribute to compile and runtime dependencies
                project.getConfigurations().named(dialectSourceSet.getCompileClasspathConfigurationName()).configure(c ->
                    c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, dialect)
                );
                project.getConfigurations().named(dialectSourceSet.getRuntimeClasspathConfigurationName()).configure(c ->
                    c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, dialect)
                );

                // Add a runtimeOnly dependency for the dialect driver
                String dependencyNotation = SQL_DIALECT_DEPENDENCIES.get(dialect);
                if (dependencyNotation != null) {
                    project.getDependencies().add(dialectSourceSet.getRuntimeOnlyConfigurationName(), dependencyNotation);
                }

                // Run H2 before other dialects
                if (dialect != SqlDialect.H2) {
                    project.getTasks().named(dialectSourceSet.getClassesTaskName()).configure(t -> t.shouldRunAfter(SqlDialect.H2.getSourceSetName() + "Classes"));
                }
            });
        }
    }
}
