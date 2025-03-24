package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.Locale;
import java.util.Objects;

import static de.rptu.cs.exclaim.gradle.SqlDialectPlugin.SQL_DIALECT_ATTRIBUTE;

/**
 * SQL Dialect Outgoing Variants Plugin
 * <p>
 * This plugin adds an outgoing variant and a jar task for each supported SQL dialect.
 */
public class SqlDialectOutgoingVariantsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            // Add a jar task for this dialect
            TaskProvider<Jar> jarTaskProvider = project.getTasks().register(dialect.getJarTaskName(), Jar.class, jarTask -> {
                jarTask.setDescription("Assembles a jar archive containing the classes for the " + dialect + " dialect.");
                jarTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                jarTask.getArchiveBaseName().set("exclaim-" + project.getName() + "-" + dialect.getSourceSetName());
                jarTask.from(sourceSets.getByName(dialect.getSourceSetName()).getOutput());
            });
            project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(t -> t.dependsOn(jarTaskProvider));

            // Add an outgoing variant for this dialect
            project.getConfigurations().consumable(dialect.name().toLowerCase(Locale.ROOT) + "RuntimeElements", c -> {
                c.setDescription("Elements of runtime for the " + dialect + " dialect.");
                c.setVisible(true);
                c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, dialect);
                c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
                c.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
                SourceSet dialectSourceSet = sourceSets.getByName(dialect.getSourceSetName());
                c.extendsFrom(
                    project.getConfigurations().getByName(dialectSourceSet.getImplementationConfigurationName()),
                    project.getConfigurations().getByName(dialectSourceSet.getRuntimeOnlyConfigurationName())
                );
                c.getOutgoing().artifact(jarTaskProvider);
                c.getOutgoing().getVariants().create("classes", v -> {
                    v.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.CLASSES));
                    v.artifact(dialectSourceSet.getOutput().getClassesDirs().getSingleFile(), a -> a.builtBy(project.getTasks().named(dialectSourceSet.getClassesTaskName())));
                });
                c.getOutgoing().getVariants().create("resources", v -> {
                    v.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.RESOURCES));
                    v.artifact(Objects.requireNonNull(dialectSourceSet.getOutput().getResourcesDir()), a -> a.builtBy(project.getTasks().named(dialectSourceSet.getProcessResourcesTaskName())));
                });
            });
        }

        // Disable the default jar task for source set main
        project.getTasks().named(JavaPlugin.JAR_TASK_NAME).configure(Utils::disableTask);

        // Disable default outgoing variants without SQL dialect
        for (String configurationName : new String[]{
            JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME,
            JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME,
        }) {
            project.getConfigurations().named(configurationName).configure(c -> {
                c.setVisible(false);
                c.getAttributes().attribute(SQL_DIALECT_ATTRIBUTE, SqlDialect.Invalid);
            });
        }
    }
}
