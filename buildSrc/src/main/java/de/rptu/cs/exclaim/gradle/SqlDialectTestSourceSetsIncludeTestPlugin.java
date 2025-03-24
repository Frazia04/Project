package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;

import static de.rptu.cs.exclaim.gradle.SqlDialectSourceSetsIncludeMainPlugin.dialectSourceSetsIncludeBaseSourceSet;

/**
 * SQL Dialect Test Source Sets Include Test Plugin
 * <p>
 * This plugin adds a separate test source set and task for each supported SQL dialect. That dialect's test source set
 * combines the default test source directory together with a dialect-specific test source directory. The default test
 * source directory is compiled for each dialect with dialect-specific dependencies. Sources in the default test source
 * directory can therefore use only database API that is the same for all supported dialects, otherwise some compilation
 * tasks will fail.
 * <p>
 * Since the default test source set is not compiled separately, the default test task is disabled. Test classes in the
 * default test source directory are used in each dialect's test task.
 */
public class SqlDialectTestSourceSetsIncludeTestPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectTestSourceSetsPlugin.class);

        // Let the dialect-specific test source set include the default test source set,
        // using the logic from SqlDialectSourceSetsIncludeMainPlugin
        dialectSourceSetsIncludeBaseSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME, SqlDialect::getTestSourceSetName);

        // Disable the default test and JaCoCo task
        for (String taskName : new String[]{
            JavaPlugin.TEST_TASK_NAME,
            "jacocoTestReport",
        }) {
            project.getTasks().named(taskName).configure(Utils::disableTask);
        }
        project.getTasks().named(JavaPlugin.TEST_TASK_NAME, Test.class).configure(task -> {
            task.setClasspath(project.files()); // clear task dependencies
        });
    }
}
