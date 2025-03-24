package de.rptu.cs.exclaim.gradle;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

/**
 * Base Plugin
 * <p>
 * We apply this plugin to all projects. It takes care of some defaults we want to have everywhere.
 */
public class BasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Apply plugins
        project.getPluginManager().apply(PlatformPlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(IdeaPlugin.class);

        // Add maven central repository
        project.getRepositories().mavenCentral();

        // Define the Java version to use in our project
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPluginExtension.setSourceCompatibility(JavaVersion.VERSION_17);
        javaPluginExtension.setTargetCompatibility(JavaVersion.VERSION_17);

        // Configure name for our .jar files
        project.getTasks().withType(Jar.class).configureEach(task -> {
            task.getArchiveBaseName().set("exclaim-" + project.getName());
            task.getArchiveVersion().set("");
        });

        // Add JSR 305 and Jetbrains annotations to compile classpath for all source sets
        project.getExtensions().getByType(SourceSetContainer.class).configureEach(s -> {
            project.getDependencies().add(s.getCompileOnlyConfigurationName(), "com.google.code.findbugs:jsr305");
            project.getDependencies().add(s.getCompileOnlyConfigurationName(), "org.jetbrains:annotations");
        });

        // IntelliJ IDEA: Download Javadoc and sources for better support
        project.getExtensions().getByType(IdeaModel.class).module(ideaModule -> {
            ideaModule.setDownloadJavadoc(true);
            ideaModule.setDownloadSources(true);
        });
    }
}
