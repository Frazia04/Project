package de.rptu.cs.exclaim.gradle;

import lombok.Getter;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Lombok Plugin
 * <p>
 * This plugin applies Lombok to all source sets.
 * <b>
 * Disable it for a specific source set:
 * <pre>{@code
 *   sourceSets.main.extensions.getByType<LombokExtension>().enabled.set(false)
 * }</pre>
 */
public class LombokPlugin implements Plugin<Project> {
    @Getter
    public static class LombokExtension {
        private final Property<Boolean> enabled;

        public LombokExtension(ObjectFactory objectFactory) {
            enabled = objectFactory.property(Boolean.class).convention(true);
            enabled.finalizeValueOnRead();
        }
    }

    @Override
    public void apply(Project project) {
        // Register the "lombok" extension in all source sets
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.configureEach(s -> s.getExtensions().create("lombok", LombokExtension.class, project.getObjects()));

        // Define a configuration to hold the Lombok dependency
        NamedDomainObjectProvider<DependencyScopeConfiguration> lombokConfigurationProvider = project.getConfigurations().dependencyScope("lombok", c -> {
            c.setDescription("Additional compile and annotation processor dependencies when Lombok is enabled.");
            c.setVisible(false);
            c.getDependencies().add(project.getDependencies().create("org.projectlombok:lombok"));
        });

        Utils.afterEvaluate(project, () -> sourceSets.configureEach(sourceSet -> {
            // Check whether Lombok is enabled for this source set
            Property<Boolean> enabledProperty = sourceSet.getExtensions().getByType(LombokExtension.class).getEnabled();

            // Add the Lombok dependency
            if (enabledProperty.get()) {
                DependencyScopeConfiguration lombok = lombokConfigurationProvider.get();
                project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(lombok);
                project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName()).extendsFrom(lombok);
            }
        }));
    }
}
