package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;

import java.util.function.Function;

/**
 * SQL Dialect Source Sets Include Main Plugin
 * <p>
 * This plugin adds a separate source set for each supported SQL dialect. That source set combines the main source
 * directory together with a dialect-specific source directory. The main source directory is compiled for each dialect
 * with dialect-specific dependencies. Sources in main can therefore use only the database API that is the same for all
 * supported dialects, otherwise some compilation tasks will fail.
 */
public class SqlDialectSourceSetsIncludeMainPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);

        // The logic in this plugin is extracted to a method for re-use in other plugins
        dialectSourceSetsIncludeBaseSourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME, SqlDialect::getSourceSetName);
    }

    static void dialectSourceSetsIncludeBaseSourceSet(Project project, String baseSourceSetName, Function<SqlDialect, String> dialectSourceSetNameGenerator) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        boolean isIntellijIdeaSync = Utils.isIntellijIdeaSync();

        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            sourceSets.named(dialectSourceSetNameGenerator.apply(dialect)).configure(dialectSourceSet -> {
                SourceSet baseSourceSet = sourceSets.getByName(baseSourceSetName);
                if (isIntellijIdeaSync) {
                    // IntelliJ IDEA does not support sharing source directories with multiple source sets.
                    // Instead, we behave like the SqlDialectSourceSetsDependMainPlugin.
                    SourceSetOutput baseSourceSetOutput = sourceSets.getByName(baseSourceSetName).getOutput();
                    dialectSourceSet.setCompileClasspath(baseSourceSetOutput.plus(dialectSourceSet.getCompileClasspath()));
                    dialectSourceSet.setRuntimeClasspath(baseSourceSetOutput.plus(dialectSourceSet.getRuntimeClasspath()));
                } else {
                    // Add base source directory as additional sources to each dialect's source set
                    dialectSourceSet.getJava().source(baseSourceSet.getJava());
                    dialectSourceSet.getResources().source(baseSourceSet.getResources());
                }

                // Also inherit compileOnly and annotationProcessor configurations from the base source set
                project.getConfigurations().named(dialectSourceSet.getCompileOnlyConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(baseSourceSet.getCompileOnlyConfigurationName())
                ));
                project.getConfigurations().named(dialectSourceSet.getAnnotationProcessorConfigurationName()).configure(c -> c.extendsFrom(
                    project.getConfigurations().getByName(baseSourceSet.getAnnotationProcessorConfigurationName())
                ));
            });
        }

        // Disable the default compile tasks for the base source set
        sourceSets.named(baseSourceSetName).configure(baseSourceSet -> {
            for (String taskName : new String[]{
                baseSourceSet.getCompileJavaTaskName(),
                baseSourceSet.getProcessResourcesTaskName(),
                baseSourceSet.getClassesTaskName(),
            }) {
                project.getTasks().named(taskName).configure(Utils::disableTask);
            }

            baseSourceSet.setCompileClasspath(isIntellijIdeaSync
                // Even though the source set's compile task is disabled, IntelliJ IDEA uses its classpath.
                // We use the H2 compile classpath for this purpose.
                ? sourceSets.getByName(dialectSourceSetNameGenerator.apply(SqlDialect.H2)).getCompileClasspath()
                // Remove dependency from default compile task
                : project.files()
            );
        });
    }
}
