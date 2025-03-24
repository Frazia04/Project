package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static de.rptu.cs.exclaim.gradle.SqlDialectPlugin.SQL_DIALECT_ATTRIBUTE;

/**
 * SQL Dialect Test Source Sets Plugin
 * <p>
 * This plugin adds a separate test source set and task for each supported SQL dialect.
 */
public class SqlDialectTestSourceSetsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);
        project.getPluginManager().apply(JUnitPlugin.class);
        project.getPluginManager().apply(JacocoAggregateReportPlugin.class);

        // The logic in this plugin is extracted to two methods for re-use in EndToEndTestPlugin
        registerDialectTestSourceSets(project, SourceSet.TEST_SOURCE_SET_NAME, SqlDialect::getTestSourceSetName);
        registerDialectTestTasks(project, SqlDialect::getTestSourceSetName, SqlDialect::getTestSourceSetName, null);
    }

    static void registerDialectTestSourceSets(Project project, String testSourceSetName, Function<SqlDialect, String> dialectTestSourceSetNameGenerator) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            sourceSets.register(dialectTestSourceSetNameGenerator.apply(dialect), dialectTestSourceSet -> {
                SourceSet dialectSourceSet = sourceSets.getByName(dialect.getSourceSetName());
                SourceSet testSourceSet = sourceSets.getByName(testSourceSetName);

                // Add output of dialect's source set to compile and runtime classpath
                SourceSetOutput dialectSourceSetOutput = dialectSourceSet.getOutput();
                dialectTestSourceSet.setCompileClasspath(dialectSourceSetOutput.plus(dialectTestSourceSet.getCompileClasspath()));
                dialectTestSourceSet.setRuntimeClasspath(dialectSourceSetOutput.plus(dialectTestSourceSet.getRuntimeClasspath()));

                // Inherit implementation and runtimeOnly configurations from the dialect's source set and from the base test source set
                project.getConfigurations().named(dialectTestSourceSet.getImplementationConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(dialectSourceSet.getImplementationConfigurationName()),
                    project.getConfigurations().getByName(testSourceSet.getImplementationConfigurationName())
                ));
                project.getConfigurations().named(dialectTestSourceSet.getRuntimeOnlyConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(dialectSourceSet.getRuntimeOnlyConfigurationName()),
                    project.getConfigurations().getByName(testSourceSet.getRuntimeOnlyConfigurationName())
                ));

                // Add the dialect attribute to compile and runtime dependencies
                project.getConfigurations().named(dialectTestSourceSet.getCompileClasspathConfigurationName()).configure(c ->
                    c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, dialect)
                );
                project.getConfigurations().named(dialectTestSourceSet.getRuntimeClasspathConfigurationName()).configure(c ->
                    c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, dialect)
                );

                // IntelliJ IDEA: Mark source set as tests
                project.getExtensions().getByType(IdeaModel.class).module(ideaModule -> {
                    ideaModule.getTestSources().from(dialectTestSourceSet.getAllJava().getSrcDirs());
                    ideaModule.getTestResources().from(dialectTestSourceSet.getResources().getSrcDirs());
                });
            });
        }

        // When using the SqlDialectSourceSetsDependMainPlugin, then we also need the output of source set main on our
        // compile and runtime classpath.
        Action<Plugin<Project>> addMainSourceSetOutput = p -> {
            SourceSetOutput mainSourceSetOutput = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
            for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
                sourceSets.named(dialectTestSourceSetNameGenerator.apply(dialect)).configure(dialectTestSourceSet -> {
                    dialectTestSourceSet.setCompileClasspath(mainSourceSetOutput.plus(dialectTestSourceSet.getCompileClasspath()));
                    dialectTestSourceSet.setRuntimeClasspath(mainSourceSetOutput.plus(dialectTestSourceSet.getRuntimeClasspath()));
                });
            }
        };
        project.getPlugins().withType(SqlDialectSourceSetsDependMainPlugin.class).all(addMainSourceSetOutput);

        // When using the SqlDialectSourceSetsIncludeMainPlugin and running an IntelliJ IDEA sync action, then we also
        // need to add the output of source set main since the plugin then behaves like the DependMain plugin.
        if (Utils.isIntellijIdeaSync()) {
            project.getPlugins().withType(SqlDialectSourceSetsIncludeMainPlugin.class).all(addMainSourceSetOutput);
        }
    }

    static void registerDialectTestTasks(Project project, Function<SqlDialect, String> dialectTestSourceSetNameGenerator, Function<SqlDialect, String> dialectTestTaskNameGenerator, @Nullable BiConsumer<TaskProvider<Test>, SqlDialect> taskConfigurationAction) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            // Add a test task for this dialect
            TaskProvider<Test> testTaskProvider = project.getTasks().register(dialectTestTaskNameGenerator.apply(dialect), Test.class, testTask -> {
                testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                testTask.setDescription("Runs the tests for the " + dialect + " dialect.");
                SourceSet dialectTestSourceSet = sourceSets.getByName(dialectTestSourceSetNameGenerator.apply(dialect));
                testTask.setTestClassesDirs(dialectTestSourceSet.getOutput().getClassesDirs());
                testTask.setClasspath(dialectTestSourceSet.getRuntimeClasspath());
                testTask.useJUnitPlatform();
                testTask.shouldRunAfter(JavaPlugin.TEST_TASK_NAME);

                // Run H2 before other dialects
                if (dialect != SqlDialect.H2) {
                    testTask.shouldRunAfter(dialectTestTaskNameGenerator.apply(SqlDialect.H2));
                }
            });
            project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(t -> t.dependsOn(testTaskProvider));

            // Register a JaCoCo report task for the test task
            JUnitPlugin.registerJacocoReportTask(
                project,
                testTaskProvider,
                sourceSets.named(dialect.getSourceSetName())
            );

            // Apply additional configuration
            if (taskConfigurationAction != null) {
                taskConfigurationAction.accept(testTaskProvider, dialect);
            }
        }

        // When using the SqlDialectSourceSetsDependMainPlugin, then source code of source set main should be part of
        // the JaCoCo report, because it is not already contained in the dialect's source set.
        project.getPlugins().withType(SqlDialectSourceSetsDependMainPlugin.class).all(p -> {
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
                project.getTasks().named(JUnitPlugin.jacocoReportTaskName(dialectTestTaskNameGenerator.apply(dialect)), JacocoReport.class).configure(reportTask ->
                    reportTask.sourceSets(mainSourceSet)
                );
            }
        });
    }
}
