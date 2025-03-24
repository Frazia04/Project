package de.rptu.cs.exclaim.gradle;

import lombok.Value;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import java.util.Map;

/**
 * Platform Plugin
 * <p>
 * 1. Adds a configuration named "platform"
 * 2. Lets all other configurations extend that platform configuration
 * 3. Adds dependency constraints (from Spring Boot and gradle.properties file) to the platform configuration
 */
public class PlatformPlugin implements Plugin<Project> {
    @Value
    private static class PlatformDependency {
        String group;
        String module;
        String versionKey; // key in gradle.properties file
    }

    private static final PlatformDependency[] PLATFORM_DEPENDENCIES = {
        new PlatformDependency("org.opensaml", "opensaml-core", "opensaml_version"),
        new PlatformDependency("org.opensaml", "opensaml-saml-api", "opensaml_version"),
        new PlatformDependency("org.opensaml", "opensaml-saml-impl", "opensaml_version"),
        new PlatformDependency("org.flywaydb", "flyway-core", "flyway_version"),
        new PlatformDependency("org.flywaydb", "flyway-database-postgresql", "flyway_version"),
        new PlatformDependency("org.jooq", "jooq", "jooq_version"),
        new PlatformDependency("org.jooq", "jooq-checker", "jooq_version"),
        new PlatformDependency("org.jooq", "jooq-codegen", "jooq_version"),
        new PlatformDependency("com.h2database", "h2", "h2_version"),
        new PlatformDependency("org.projectlombok", "lombok", "lombok_version"),
        new PlatformDependency("com.google.errorprone", "error_prone_core", "errorprone_version"),
        new PlatformDependency("com.google.errorprone", "error_prone_test_helpers", "errorprone_version"),
        new PlatformDependency("com.uber.nullaway", "nullaway", "nullaway_version"),
        new PlatformDependency("com.google.code.findbugs", "jsr305", "jsr305_version"),
        new PlatformDependency("org.jetbrains", "annotations", "jetbrains_annotations_version"),
        new PlatformDependency("cz.habarta.typescript-generator", "typescript-generator-core", "typescript_generator_version"),
    };

    @Override
    public void apply(Project project) {
        // Define a configuration to hold the dependency constraints
        DependencyScopeConfiguration platformConfiguration = project.getConfigurations().dependencyScope("platform", c -> {
            c.setDescription("Dependency constraints");
            c.setVisible(false);
        }).get();

        // Let all configurations extend the platform configuration
        project.getConfigurations().configureEach(c -> {
            if (c != platformConfiguration) {
                c.extendsFrom(platformConfiguration);
            }
        });

        // Add the Spring Boot platform dependencies, but exclude the modules we define ourselves
        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
        ModuleDependency springBootPlatform = (ModuleDependency) project.getDependencies().platform(
            "org.springframework.boot:spring-boot-dependencies:" + extraProperties.get("spring_boot_version")
        );
        for (PlatformDependency d : PLATFORM_DEPENDENCIES) {
            springBootPlatform.exclude(Map.of("group", d.group, "module", d.module));
        }
        springBootPlatform.exclude(Map.of("group", "org.seleniumhq.selenium")); // exclude Selenium (see EndToEndTestsPlugin)
        platformConfiguration.getDependencies().add(springBootPlatform);

        // Add our platform dependencies
        for (PlatformDependency d : PLATFORM_DEPENDENCIES) {
            platformConfiguration.getDependencyConstraints().add(project.getDependencies().getConstraints().create(
                d.group + ":" + d.module + ":" + extraProperties.get(d.versionKey) + "!!"
            ));
        }
    }
}
