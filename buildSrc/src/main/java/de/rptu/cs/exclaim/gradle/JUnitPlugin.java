package de.rptu.cs.exclaim.gradle;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.Locale;

import static org.gradle.api.plugins.JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME;

/**
 * JUnit Plugin
 * <p>
 * This plugin adds dependencies for the JUnit testing framework and the JaCoCo Java Code Coverage Library.
 */
public class JUnitPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Apply the JaCoCo plugin
        project.getPluginManager().apply(JacocoPlugin.class);

        // Configure the default test suite
        project.getExtensions().getByType(TestingExtension.class).getSuites().named(DEFAULT_TEST_SUITE_NAME, JvmTestSuite.class).configure(suite -> {
            // Enable JUnit Jupiter (implicitly adds the dependency)
            suite.useJUnitJupiter();

            // Automatically run the JaCoCo task
            suite.getTargets().configureEach(target -> target.getTestTask().configure(testTask ->
                testTask.finalizedBy(project.getTasks().named("jacocoTestReport"))
            ));
        });

        // Add a system property with Gradle's current log level
        project.getTasks().withType(Test.class).configureEach(task ->
            task.systemProperty("exclaim.log_level", Utils.detectSlf4jLogLevel(task.getLogger()))
        );

        // When using the SqlDialectSourceSetsIncludeMainPlugin, then source set main cannot be compiled itself.
        // Therefore, there is no input for the default test task, hence we need to disable it.
        project.getPlugins().withType(SqlDialectSourceSetsIncludeMainPlugin.class).all(p -> {
            for (String taskName : new String[]{
                JavaPlugin.TEST_TASK_NAME,
                "jacocoTestReport",
            }) {
                project.getTasks().named(taskName).configure(Utils::disableTask);
            }
        });
    }

    static void registerJacocoReportTask(Project project, TaskProvider<Test> testTaskProvider, NamedDomainObjectProvider<SourceSet> sourceSetProvider) {
        TaskProvider<JacocoReport> jacocoReportTaskProvider = project.getTasks().register(jacocoReportTaskName(testTaskProvider.getName()), JacocoReport.class, reportTask -> {
            reportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            reportTask.setDescription("Generates code coverage report for the " + testTaskProvider.getName() + " task.");
            reportTask.executionData(testTaskProvider.get());
            reportTask.sourceSets(sourceSetProvider.get());
            Utils.fixJacocoReportsDir(project, reportTask, testTaskProvider.getName());
        });
        testTaskProvider.configure(t -> t.finalizedBy(jacocoReportTaskProvider));
    }

    static String jacocoReportTaskName(String testTaskName) {
        return "jacoco" + testTaskName.substring(0, 1).toUpperCase(Locale.ROOT) + testTaskName.substring(1) + "Report";
    }
}
