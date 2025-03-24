package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.Jar;

/**
 * SQL Dialect Source Sets Depend Main Plugin
 * <p>
 * This plugin adds a separate source set for each supported SQL dialect. Source set main is compiled only once, without
 * database dependencies. Each dialect's source set depends on the output of main. Database API can only be used in the
 * dialect-specific source sets, not in main.
 */
public class SqlDialectSourceSetsDependMainPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        // Add output of source set main to compile and runtime classpath of each dialect's source set
        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            sourceSets.named(dialect.getSourceSetName()).configure(dialectSourceSet -> {
                SourceSetOutput mainSourceSetOutput = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
                dialectSourceSet.setCompileClasspath(mainSourceSetOutput.plus(dialectSourceSet.getCompileClasspath()));
                dialectSourceSet.setRuntimeClasspath(mainSourceSetOutput.plus(dialectSourceSet.getRuntimeClasspath()));
            });
        }

        // Add output of source set main to each dialect's jar file (if there actually is a dialect-specific jar task)
        project.getPlugins().withType(SqlDialectOutgoingVariantsPlugin.class).all(p -> {
            for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
                project.getTasks().named(dialect.getJarTaskName(), Jar.class).configure(jarTask ->
                    jarTask.from(sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
                );
            }
        });
    }
}
