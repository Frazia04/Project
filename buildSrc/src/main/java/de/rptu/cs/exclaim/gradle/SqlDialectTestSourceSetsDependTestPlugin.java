package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.testing.Test;

/**
 * SQL Dialect Test Source Sets Depend Test Plugin
 * <p>
 * This plugin adds a separate test source set and task for each supported SQL dialect. The default test source set is
 * compiled only once, without database dependencies. Each dialect's test source set depends on the output of the
 * default test source set. Database API can only be used in the dialect-specific test source sets.
 * <p>
 * Since source set test ends up in each dialect's test task, the default test task is disabled.
 */
public class SqlDialectTestSourceSetsDependTestPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectTestSourceSetsPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        // Add output of source set test to each dialect's test suite
        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            sourceSets.named(dialect.getTestSourceSetName()).configure(dialectTestSourceSet -> {
                SourceSetOutput testSourceSetOutput = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput();
                dialectTestSourceSet.setCompileClasspath(testSourceSetOutput.plus(dialectTestSourceSet.getCompileClasspath()));
                dialectTestSourceSet.setRuntimeClasspath(testSourceSetOutput.plus(dialectTestSourceSet.getRuntimeClasspath()));
            });
            project.getTasks().named(dialect.getTestSourceSetName(), Test.class).configure(testTask ->
                testTask.setTestClassesDirs(testTask.getTestClassesDirs().plus(sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput()))
            );
        }

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
