package de.rptu.cs.exclaim.gradle;

import com.github.gradle.node.NodeExtension;
import com.github.gradle.node.NodePlugin;
import com.github.gradle.node.npm.task.NpmInstallTask;
import com.github.gradle.node.npm.task.NpmTask;
import com.github.gradle.node.task.NodeSetupTask;
import lombok.Getter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.rptu.cs.exclaim.gradle.Utils.detectLogLevel;

/**
 * Node.js Plugin
 * <p>
 * This plugin applies and configures the
 * <a href="https://github.com/node-gradle/gradle-node-plugin">Gradle Plugin for Node</a>.
 */
public class NodeJsPlugin implements Plugin<Project> {
    public static final String FRONTEND_GROUP = "frontend";

    /**
     * Extension to allow configuring our plugin in build.gradle.kts
     */
    @Getter
    public static class NodeJsPluginExtension {
        /**
         * Output dir for the npmRunBuild task (default: "dist")
         */
        private final Property<String> outputDir;

        /**
         * Dirs that are not tracked as inputs, in addition to outputDir
         */
        private final ListProperty<String> excludeFromInputs;

        public NodeJsPluginExtension(ObjectFactory objectFactory) {
            outputDir = objectFactory.property(String.class).convention("dist");
            outputDir.finalizeValueOnRead();
            excludeFromInputs = objectFactory.listProperty(String.class);
            excludeFromInputs.finalizeValueOnRead();
        }
    }

    // Build service to ensure that only one NodeSetupTask task executes.
    public static abstract class NodeSetupBuildService implements BuildService<BuildServiceParameters.None> {
        private final AtomicBoolean haveExecuted = new AtomicBoolean(false);

        public boolean shouldExecute() {
            return !haveExecuted.getAndSet(true);
        }
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(NodePlugin.class);
        NodeExtension nodeExtension = project.getExtensions().getByType(NodeExtension.class);

        // Add our plugin extension
        NodeJsPluginExtension exclaimNodeJsExtension = project.getExtensions().create("exclaimNodeJs", NodeJsPluginExtension.class);

        // Download Node.js version defined in gradle.properties to the root project .gradle folder, such that it is
        // shared between all subprojects using this plugin.
        nodeExtension.getDownload().set(true);
        nodeExtension.getVersion().set((String) project.getExtensions().getExtraProperties().get("nodejs_version"));
        nodeExtension.getWorkDir().set(project.getRootProject().getLayout().getProjectDirectory().dir(".gradle").dir("nodejs"));

        // Since we share a node installation between all subprojects, we only need to execute one NodeSetupTask.
        // The actual download goes through Gradle's repository download mechanism and therefore is cached even when
        // up-to-date checks fail. Our optimization only saves time extracting the distribution multiple times and
        // avoids errors with parallel execution of multiple NodeSetupTasks.
        Provider<NodeSetupBuildService> buildServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("NodeSetupTaskMutex", NodeSetupBuildService.class, spec ->
            spec.getMaxParallelUsages().set(1)
        );
        project.getTasks().named(NodeSetupTask.NAME, NodeSetupTask.class).configure(task -> {
            task.usesService(buildServiceProvider);
            task.onlyIf(task1 -> buildServiceProvider.get().shouldExecute());
        });

        // Always run "npm ci" instead of "npm install" in order to treat package.json as frozen when running through
        // Gradle. Frontend developers will use their own npm installation and call "npm install" directly.
        nodeExtension.getNpmInstallCommand().set("ci");

        // Do not track the whole node_modules dir, only node_modules/.package-lock.json
        nodeExtension.getFastNpmInstall().set(true);

        // Register a task to update the "version" field in package.json
        TaskProvider<NpmTask> npmSetPackageVersion = project.getTasks().register("npmSetPackageVersion", NpmTask.class, task -> {
            // Task meta data
            task.setDescription("Updates the 'version' key in package.json.");

            // Command
            task.getNpmCommand().set(project.provider(() -> List.of(
                "pkg", "set", "version=" + project.getVersion()
            )));

            // Task input/outputs
            Provider<RegularFile> packageJson = nodeExtension.getNodeProjectDir().map(nodeProjectDir ->
                nodeProjectDir.file("package.json")
            );
            task.getInputs().file(packageJson);
            task.getOutputs().file(packageJson);
            task.getOutputs().cacheIf(task1 -> true);
        });

        // Configure npmInstall to depend on npmSetPackageVersion
        TaskProvider<NpmInstallTask> npmInstall = project.getTasks().named(NpmInstallTask.NAME, NpmInstallTask.class, task -> {
            task.getInputs().files(npmSetPackageVersion);

            // Workaround for https://github.com/node-gradle/gradle-node-plugin/issues/310
            task.getOutputs().file(nodeExtension.getNodeProjectDir().map(nodeProjectDir -> nodeProjectDir
                .file("node_modules/.package-lock.json")
            ));
        });

        // Configuration for all npm tasks
        project.getTasks().withType(NpmTask.class, task -> {
            // Set log level environment variables
            LogLevel logLevel = detectLogLevel(task.getLogger());
            task.getEnvironment().putAll(Map.of(
                "NPM_CONFIG_LOGLEVEL", npmLogLevel(logLevel),
                "VITE_LOG_LEVEL", viteLogLevel(logLevel),
                "ANTORA_LOG_LEVEL", antoraLogLevel(logLevel)
            ));

            // Let all "npm run ..." tasks depend on npmInstall and add the project directory as task inputs
            if (task.getName().startsWith("npmRun")) {
                task.getInputs().files(
                    npmInstall,
                    nodeExtension.getNodeProjectDir().flatMap(nodeProjectDir ->
                        exclaimNodeJsExtension.getOutputDir().flatMap(outputDir ->
                            exclaimNodeJsExtension.getExcludeFromInputs().map(excluded ->
                                project.fileTree(nodeProjectDir, spec -> spec
                                    .exclude("node_modules")
                                    .exclude(outputDir)
                                    .exclude(excluded)
                                )
                            )
                        )
                    )
                );
            }
        });

        // Register a task to execute "npm run build"
        TaskProvider<NpmTask> npmRunBuild = project.getTasks().register("npmRunBuild", NpmTask.class, task -> {
            // Task meta data
            task.setGroup(FRONTEND_GROUP);
            task.setDescription("Builds the " + project.getName() + ".");

            // Command
            task.getNpmCommand().set(List.of("run", "build"));

            // Task outputs
            task.getOutputs().dir(nodeExtension.getNodeProjectDir().flatMap(nodeProjectDir ->
                exclaimNodeJsExtension.getOutputDir().map(nodeProjectDir::dir))
            );
            task.getOutputs().cacheIf(task1 -> true);
        });

        // Make npmRunBuild part of assemble lifecycle
        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, task ->
            task.dependsOn(npmRunBuild)
        );

        // Add additional dirs to the clean task
        project.getTasks().named(LifecycleBasePlugin.CLEAN_TASK_NAME, Delete.class, task -> task.delete(
            "node_modules", npmRunBuild
        ));
    }

    public static TaskProvider<NpmTask> addCheckTask(Project project) {
        // Register a task to execute "npm run check"
        TaskProvider<NpmTask> npmRunCheck = project.getTasks().register("npmRunCheck", NpmTask.class, task -> {
            // Task meta data
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Checks the " + project.getName() + " code.");

            // Command
            task.getNpmCommand().set(List.of("run", "check"));
        });

        // Let the check task depend on our check task
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(npmRunCheck));

        return npmRunCheck;
    }

    private static String npmLogLevel(LogLevel logLevel) {
        // See https://docs.npmjs.com/cli/using-npm/logging
        // npm default log level is "notice". All levels except for "silent" have output on regular command execution,
        // which is undesirable in Gradle's default log level (LIFECYCLE).
        return switch (logLevel) {
            case DEBUG -> "verbose";
            case INFO -> "notice";
            case LIFECYCLE, WARN, QUIET, ERROR -> "silent";
        };
    }

    private static String viteLogLevel(LogLevel logLevel) {
        // See https://vite.dev/guide/cli.html#options
        // Vite default is "info", which is too verbose for Gradle's default log level (LIFECYCLE).
        return switch (logLevel) {
            case DEBUG, INFO -> "info";
            case LIFECYCLE, WARN -> "warn";
            case QUIET, ERROR -> "error";
        };
    }

    private static String antoraLogLevel(LogLevel logLevel) {
        // See https://docs.antora.org/antora/latest/playbook/runtime-log-level/#level-key
        return switch (logLevel) {
            case DEBUG, INFO -> "info";
            case LIFECYCLE, WARN -> "warn";
            case QUIET, ERROR -> "error";
        };
    }
}
