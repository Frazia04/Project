package de.rptu.cs.exclaim.gradle;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.base.TestingExtension;

/**
 * JUnit Integration Tests Plugin
 * <p>
 * This plugin adds a separate test suite for integration tests.
 */
public class JUnitIntegrationTestsPlugin implements Plugin<Project> {
    public static final String INTEGRATION_TEST_NAME = "integrationTest";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JUnitPlugin.class);
        project.getPluginManager().apply(JacocoAggregateReportPlugin.class);

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        NamedDomainObjectProvider<JvmTestSuite> testSuite = project.getExtensions().getByType(TestingExtension.class).getSuites().register(INTEGRATION_TEST_NAME, JvmTestSuite.class, suite -> {
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            SourceSetOutput mainSourceSetOutput = mainSourceSet.getOutput();
            SourceSet integrationTestSourceSet = suite.getSources();

            // Add mainSourceSetOutput to compile and runtime classpath
            integrationTestSourceSet.setCompileClasspath(mainSourceSetOutput.plus(integrationTestSourceSet.getCompileClasspath()));
            integrationTestSourceSet.setRuntimeClasspath(mainSourceSetOutput.plus(integrationTestSourceSet.getRuntimeClasspath()));

            // Inherit implementation and runtimeOnly configurations from mainSourceSet
            project.getConfigurations().named(integrationTestSourceSet.getImplementationConfigurationName()).configure(c -> c.extendsFrom(
                project.getConfigurations().getByName(mainSourceSet.getImplementationConfigurationName())
            ));
            project.getConfigurations().named(integrationTestSourceSet.getRuntimeOnlyConfigurationName()).configure(c -> c.extendsFrom(
                project.getConfigurations().getByName(mainSourceSet.getRuntimeOnlyConfigurationName())
            ));

            // Enable JUnit Jupiter (implicitly adds the dependency)
            suite.useJUnitJupiter();

            // Integration tests should run after unit tests
            suite.getTargets().configureEach(target -> target.getTestTask().configure(testTask ->
                testTask.shouldRunAfter(JavaPlugin.TEST_TASK_NAME)
            ));
        });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(t -> t.dependsOn(testSuite));

        // Register task for JaCoCo reports on integration tests
        JUnitPlugin.registerJacocoReportTask(
            project,
            project.getTasks().named(INTEGRATION_TEST_NAME, Test.class),
            sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME)
        );
    }
}
