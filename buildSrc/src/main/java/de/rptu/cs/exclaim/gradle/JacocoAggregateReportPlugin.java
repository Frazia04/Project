package de.rptu.cs.exclaim.gradle;

import lombok.RequiredArgsConstructor;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * JaCoCo Aggregate Report Plugin
 * <p>
 * This plugin adds a task to generate an aggregate JaCoCo report. The task executes as part of the 'check' lifecycle
 * task. We do not add the aggregate report as finalizer to each test because that would trigger all tests to execute
 * when just a single test task is executed.
 */
public class JacocoAggregateReportPlugin implements Plugin<Project> {
    public static final String JACOCO_AGGREGATE_REPORT_TASK_NAME = "jacocoReport";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JacocoPlugin.class);
        TaskProvider<JacocoReport> jacocoReportTaskProvider = project.getTasks().register(JACOCO_AGGREGATE_REPORT_TASK_NAME, JacocoReport.class, aggregateReportTask -> {
            aggregateReportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            aggregateReportTask.setDescription("Generates aggregate code coverage report for all test tasks.");
            Utils.fixJacocoReportsDir(project, aggregateReportTask, "all");

            // Copy report attributes from all other report tasks
            for (var attribute : List.<Function<JacocoReport, ConfigurableFileCollection>>of(
                JacocoReport::getExecutionData,
                JacocoReport::getSourceDirectories,
                JacocoReport::getClassDirectories,
                JacocoReport::getAdditionalSourceDirs
            )) {
                // Use a Callable such that the list of report tasks is evaluated as late as possible; otherwise we miss
                // report tasks that are added after executing the aggregate report task configuration action.
                attribute.apply(aggregateReportTask).from((Callable<List<ConfigurableFileCollection>>) () ->
                    project.getTasks().withType(JacocoReport.class).stream()
                        .filter(reportTask -> reportTask != aggregateReportTask)
                        .map(attribute)
                        .toList()
                );
            }

            // Copy task dependencies from all other report tasks. Again, use a Callable to defer evaluation.
            @RequiredArgsConstructor
            class CopyDependency<T> {
                final Function<Task, T> getter;
                final BiConsumer<Task, Callable<List<T>>> setter;

                void copyDependency() {
                    setter.accept(aggregateReportTask, () ->
                        project.getTasks().withType(JacocoReport.class).stream()
                            .filter(reportTask -> reportTask != aggregateReportTask)
                            .map(getter)
                            .toList()
                    );
                }
            }
            List.of(
                new CopyDependency<>(Task::getDependsOn, Task::dependsOn),
                new CopyDependency<>(Task::getMustRunAfter, Task::mustRunAfter),
                new CopyDependency<>(Task::getShouldRunAfter, Task::shouldRunAfter)
            ).forEach(CopyDependency::copyDependency);
        });

        // Add aggregate report task as check task
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task ->
            task.dependsOn(jacocoReportTaskProvider)
        );
    }
}
