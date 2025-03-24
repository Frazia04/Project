package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.springframework.boot.gradle.dsl.SpringBootExtension;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage;
import org.springframework.boot.gradle.tasks.bundling.BootJar;
import org.springframework.boot.gradle.tasks.run.BootRun;

import java.util.Locale;
import java.util.Map;

/**
 * SQL Dialect Spring Boot Plugin
 * <p>
 * This plugin adds bootRun and bootJar tasks for each supported SQL dialect.
 */
public class SqlDialectSpringBootPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);
        project.getPluginManager().apply(SpringBootPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        Property<String> mainClassProperty = project.getExtensions().getByType(SpringBootExtension.class).getMainClass();

        for (SqlDialect dialect : SqlDialect.VALID_DIALECTS) {
            // bootJar
            TaskProvider<BootJar> dialectBootJarTaskProvider = project.getTasks().register(SpringBootPlugin.BOOT_JAR_TASK_NAME + dialect.name(), BootJar.class, task -> {
                task.setDescription("Assembles an executable jar archive containing the main classes and their dependencies for " + dialect + ".");
                task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                task.getArchiveBaseName().set("exclaim-" + dialect.name().toLowerCase(Locale.ROOT));
                task.getTargetJavaVersion().set(project.provider(javaPluginExtension::getTargetCompatibility));
                task.getMainClass().set(mainClassProperty);
                SourceSet dialectSourceSet = sourceSets.getByName(dialect.getSourceSetName());
                task.setClasspath(dialectSourceSet.getOutput()
                    .plus(dialectSourceSet.getRuntimeClasspath())
                );
                task.manifest(manifest -> manifest.attributes(Map.of("Implementation-Variant", dialect.name())));
            });
            project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(t -> t.dependsOn(dialectBootJarTaskProvider));

            // bootRun
            project.getTasks().register("bootRun" + dialect.name(), BootRun.class, task -> {
                task.setDescription("Runs this project as a Spring Boot application with " + dialect + ".");
                task.setGroup(ApplicationPlugin.APPLICATION_GROUP);
                task.getMainClass().set(mainClassProperty);
                task.getJavaLauncher().convention(project.getTasks().named("bootRun", BootRun.class).get().getJavaLauncher());
                SourceSet dialectSourceSet = sourceSets.getByName(dialect.getSourceSetName());
                task.setClasspath(dialectSourceSet.getOutput()
                    .plus(dialectSourceSet.getRuntimeClasspath())
                    .plus(project.getConfigurations().getByName(SpringBootPlugin.DEVELOPMENT_ONLY_CONFIGURATION_NAME))
                );
                task.getSystemProperties().putAll(Map.of(
                    "exclaim.version", project.getVersion().toString(),
                    "exclaim.variant", dialect.name()
                ));
            });

            // bootBuildImage
            project.getTasks().register(SpringBootPlugin.BOOT_BUILD_IMAGE_TASK_NAME + dialect.name(), BootBuildImage.class, task -> {
                BootJar dialectBootJarTask = dialectBootJarTaskProvider.get();
                task.setDescription("Builds an OCI image of the application using the output of the " + dialectBootJarTask.getName() + " task.");
                task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                task.getArchiveFile().set(dialectBootJarTask.getArchiveFile());
                String imageName = "exclaim-" + dialect.name().toLowerCase(Locale.ROOT);
                if (!Project.DEFAULT_VERSION.equals(project.getVersion())) {
                    imageName += ":" + project.getVersion();
                }
                task.getImageName().set(imageName);
            });
        }

        // Disable the default jar, bootJar, bootRun, and bootBuildImage tasks
        for (String taskName : new String[]{
            JavaPlugin.JAR_TASK_NAME,
            SpringBootPlugin.BOOT_JAR_TASK_NAME,
            "bootRun",
            SpringBootPlugin.BOOT_BUILD_IMAGE_TASK_NAME,
        }) {
            project.getTasks().named(taskName).configure(Utils::disableTask);
        }
        project.getTasks().named(SpringBootPlugin.BOOT_JAR_TASK_NAME, BootJar.class).configure(task -> {
            task.setClasspath(project.files()); // clear task dependencies
            task.getMainClass().set(""); // avoid dependency on task bootJarMainClassName
        });
        project.getTasks().named("bootRun", BootRun.class).configure(task -> {
            task.setClasspath(project.files()); // clear task dependencies
            task.getMainClass().set(""); // avoid dependency on task bootRunMainClassName
        });
    }
}
