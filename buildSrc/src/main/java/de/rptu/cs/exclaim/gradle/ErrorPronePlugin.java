package de.rptu.cs.exclaim.gradle;

import lombok.Getter;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Error Prone Plugin
 * <p>
 * This plugin applies Error Prone to all source sets.
 * <p>
 * Disable it for a specific source set:
 * <pre>{@code
 *   sourceSets.main.extensions.getByType<ErrorProneExtension>().enabled.set(false)
 * }</pre>
 * <p>
 * Disable NullAway:
 * <pre>{@code
 *   sourceSets.main.extensions.getByType<ErrorProneExtension>().nullaway.set(false)
 * }</pre>
 * <p>
 * Disable jOOQ-checker:
 * <pre>{@code
 *   sourceSets.main.extensions.getByType<ErrorProneExtension>().jooqChecker.set(false)
 * }</pre>
 * <p>
 * Set options:
 * <pre>{@code
 *   sourceSets.main.extensions.getByType<ErrorProneExtension>().options.add("-Xep:DefaultPackage:OFF")
 * }</pre>
 * <p>
 * See <a href="https://errorprone.info/docs/flags">the available options</a>
 */
public class ErrorPronePlugin implements Plugin<Project> {
    // JVM arguments required to run the compiler with Error Prone
    public static final List<String> COMPILER_JVM_ARGS = List.of(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
    );

    // Compiler arguments required to compile Error Prone classes
    public static final List<String> COMPILER_ARGS = List.of(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
    );

    @Getter
    public static class ErrorProneExtension {
        private final Property<Boolean> enabled;
        private final Property<Boolean> nullaway;
        private final Property<Boolean> jooqChecker;
        private final ListProperty<String> options;

        public ErrorProneExtension(ObjectFactory objectFactory) {
            enabled = objectFactory.property(Boolean.class).convention(true);
            enabled.finalizeValueOnRead();
            nullaway = objectFactory.property(Boolean.class).convention(true);
            nullaway.finalizeValueOnRead();
            jooqChecker = objectFactory.property(Boolean.class).convention(true);
            jooqChecker.finalizeValueOnRead();
            options = objectFactory.listProperty(String.class);
            options.finalizeValueOnRead();
        }
    }

    @Override
    public void apply(Project project) {
        // Register the "errorprone" extension in all source sets
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.configureEach(s -> s.getExtensions().create("errorprone", ErrorProneExtension.class, project.getObjects()));

        // Define configurations to hold the different dependencies
        NamedDomainObjectProvider<DependencyScopeConfiguration> errorproneConfigurationProvider = project.getConfigurations().dependencyScope("errorprone", c -> {
            c.setDescription("Additional annotation processor dependencies when Error Prone is enabled.");
            c.setVisible(false);
            c.getDependencies().add(project.getDependencies().create("com.google.errorprone:error_prone_core"));
        });
        NamedDomainObjectProvider<DependencyScopeConfiguration> nullawayConfigurationProvider = project.getConfigurations().dependencyScope("nullaway", c -> {
            c.setDescription("Additional annotation processor dependencies when NullAway is enabled.");
            c.setVisible(false);
            c.getDependencies().add(project.getDependencies().create("com.uber.nullaway:nullaway"));
        });
        NamedDomainObjectProvider<DependencyScopeConfiguration> jooqCheckerConfigurationProvider = project.getConfigurations().dependencyScope("jooqChecker", c -> {
            c.setDescription("Additional annotation processor dependencies when jOOQ-checker is enabled.");
            c.setVisible(false);
            c.getDependencies().add(((ModuleDependency) project.getDependencies().create("org.jooq:jooq-checker"))
                // This should have been a compile-only dependency of jooq-checker
                .exclude(Map.of("group", "com.google.auto.service", "module", "auto-service"))
            );
        });

        Utils.afterEvaluate(project, () -> sourceSets.configureEach(sourceSet -> {
            // Check whether Error Prone is enabled for this source set
            ErrorProneExtension errorProneExtension = sourceSet.getExtensions().getByType(ErrorProneExtension.class);
            if (errorProneExtension.getEnabled().get()) {
                // Add the Error Prone dependency
                Configuration annotationProcessorConfiguration = project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName());
                annotationProcessorConfiguration.extendsFrom(errorproneConfigurationProvider.get());

                // If enabled, also add the NullAway dependency
                boolean nullaway = errorProneExtension.getNullaway().get();
                if (nullaway) {
                    annotationProcessorConfiguration.extendsFrom(nullawayConfigurationProvider.get());
                }

                // If enabled, also add the jOOQ-checker dependency
                if (errorProneExtension.getJooqChecker().get()) {
                    annotationProcessorConfiguration.extendsFrom(jooqCheckerConfigurationProvider.get());
                }

                // Configure the JavaCompile task for this source set
                project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
                    // Construct plugin options string
                    StringBuilder options = new StringBuilder("-Xplugin:ErrorProne -XepDisableWarningsInGeneratedCode");

                    // Add NullAway options
                    if (nullaway) {
                        options.append(" -XepOpt:NullAway:AnnotatedPackages=de.rptu.cs.exclaim")
                            .append(" -XepOpt:NullAway:AcknowledgeRestrictiveAnnotations=true");
                    }

                    // Add custom options
                    for (String option : errorProneExtension.getOptions().get()) {
                        options.append(' ').append(option);
                    }

                    // Add the options to the compiler.
                    // See https://errorprone.info/docs/installation#command-line
                    task.getOptions().setFork(true);
                    Objects.requireNonNull(task.getOptions().getForkOptions().getJvmArgs()).addAll(COMPILER_JVM_ARGS);
                    task.getOptions().getCompilerArgs().addAll(List.of(
                        "-XDcompilePolicy=simple",
                        options.toString(),

                        // Also enable javac linting
                        "-Xlint:all,-processing,-serial"
                    ));
                });
            }
        }));
    }
}
