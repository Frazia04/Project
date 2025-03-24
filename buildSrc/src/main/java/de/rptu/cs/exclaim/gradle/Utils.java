package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.inject.Inject;
import java.io.File;
import java.util.Locale;

public class Utils {
    /**
     * Convert the first character of the given string to upper case.
     */
    public static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Adds an action to execute immediately after the project is evaluated. Executes the action immediately if the
     * project has already been evaluated.
     *
     * @param project  the project that must be evaluated before the action can be executed
     * @param runnable the action to execute
     */
    public static void afterEvaluate(Project project, Runnable runnable) {
        // Project.afterEvaluate is ignored if the project has already been evaluated, so check the project state first.
        // https://github.com/gradle/gradle/issues/1135
        if (project.getState().getExecuted()) {
            runnable.run();
        } else {
            project.afterEvaluate(project1 -> runnable.run());
        }
    }

    /**
     * Use the same naming convention for the reports dir as for the default report task.
     * See <a href="https://github.com/gradle/gradle/issues/6343">GitHub Issue</a> and
     * <a href="https://github.com/gradle/gradle/blob/v8.7.0/platforms/jvm/jacoco/src/main/java/org/gradle/testing/jacoco/plugins/JacocoPlugin.java#L232-L244">source code comment</a>.
     */
    public static void fixJacocoReportsDir(Project project, JacocoReport reportTask, String testTaskName) {
        DirectoryProperty reportsDir = project.getExtensions().getByType(JacocoPluginExtension.class).getReportsDirectory();
        reportTask.getReports().all(report -> {
            if (report.getOutputType() == Report.OutputType.DIRECTORY) {
                ((DirectoryReport) report).getOutputLocation().set(reportsDir.dir(testTaskName + "/" + report.getName()));
            } else {
                ((SingleFileReport) report).getOutputLocation().set(reportsDir.file(testTaskName + "/" + reportTask.getName() + "." + report.getName()));
            }
        });
    }

    /**
     * Disable the given task and clear its group such that it disappears from the task list.
     */
    public static void disableTask(Task task) {
        task.setEnabled(false);
        task.setGroup(null);
    }

    /**
     * Detect the current log level of the given logger.
     */
    public static LogLevel detectLogLevel(Logger logger) {
        if (logger.isDebugEnabled()) {
            return LogLevel.DEBUG;
        }
        if (logger.isInfoEnabled()) {
            return LogLevel.INFO;
        }
        if (logger.isLifecycleEnabled()) {
            return LogLevel.LIFECYCLE;
        }
        if (logger.isWarnEnabled()) {
            return LogLevel.WARN;
        }
        if (logger.isQuietEnabled()) {
            return LogLevel.QUIET;
        }
        return LogLevel.ERROR;
    }

    /**
     * Get the SLF4J-compatible log level for the given Gradle log level.
     */
    public static String slf4jLogLevel(LogLevel logLevel) {
        return switch (logLevel) {
            case DEBUG -> "DEBUG";
            case INFO -> "INFO";
            case LIFECYCLE, WARN -> "WARN";
            case QUIET, ERROR -> "ERROR";
        };
    }

    /**
     * Detect the current SLF4J-compatible log level of a given Logger.
     */
    public static String detectSlf4jLogLevel(Logger logger) {
        return slf4jLogLevel(detectLogLevel(logger));
    }

    /**
     * Check whether the current build is an IntelliJ IDEA Gradle sync action.
     */
    public static boolean isIntellijIdeaSync() {
        // "idea.sync.active" was introduced in 2019.1
        if (Boolean.parseBoolean(System.getProperty("idea.sync.active"))) {
            return true;
        }

        // Before 2019.1, "idea.active" was true only on sync.
        // But since 2019.1 "idea.active" is true also in task execution.
        String ideaVersion = System.getProperty("idea.version");
        if (ideaVersion != null) {
            String[] version = ideaVersion.split("\\.", 2);
            try {
                if (Integer.parseInt(version[0]) < 2019) {
                    return Boolean.parseBoolean(System.getProperty("idea.active"));
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers to execute project.delete(...) in a doFirst action, compatible with Gradle's configuration cache.
    // See https://docs.gradle.org/8.7/userguide/configuration_cache.html#config_cache:requirements:use_project_during_execution

    public static void doFirstDelete(Task task, File file) {
        FileSystemOperations fs = task.getProject().getObjects().newInstance(FileSystemOperationsContainer.class).getFs();
        // noinspection Convert2Lambda (cannot use lambda expression here due to limitation by Gradle)
        task.doFirst(new Action<>() {
            @Override
            public void execute(Task task) {
                fs.delete(spec -> spec.delete(file));
            }
        });
    }

    public static void doFirstDelete(Task task, FileSystemLocation file) {
        doFirstDelete(task, file.getAsFile());
    }

    public interface FileSystemOperationsContainer {
        @SuppressWarnings("JavaxInjectOnAbstractMethod")
        @Inject
        FileSystemOperations getFs();
    }
}
