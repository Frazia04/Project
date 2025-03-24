package de.rptu.cs.exclaim.gradle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TestcontainersUtils {
    @RequiredArgsConstructor
    public static class CallbackParameters<TASK extends Task, CONTAINER extends GenericContainer<?>> {
        public final TASK task;
        public final CONTAINER container;

        /**
         * Whether the container is fresh, i.e. it has not been used by an earlier task in the Gradle build.
         */
        public final boolean isFirstUse;
    }

    /**
     * Provide a Docker container to a task. The container is started (and later stopped) by a shared build service,
     * triggered from a doFirst action of the task. The callback allows to further configure the task, e.g. setting
     * a system property to the container ip address.
     *
     * @param project                  the project which the task is in
     * @param taskProvider             the task provider
     * @param containerDescription     a human-readable (one-word) description for the provided service ("database", "browser", ...)
     * @param containerClass           Testcontainers container class to use
     * @param dockerImageName          name (including version) of the Docker image to use
     * @param containerStartedCallback callback that is executed when the container has started
     * @param <TASK>                   type of the Gradle task
     * @param <CONTAINER>              type of the Testcontainers container
     */
    public static <TASK extends Task, CONTAINER extends GenericContainer<?>> void addTestcontainersContainerToTask(Project project, TaskProvider<TASK> taskProvider, String containerDescription, Class<? extends CONTAINER> containerClass, String dockerImageName, Consumer<CallbackParameters<TASK, CONTAINER>> containerStartedCallback) {
        // Register a build service for the desired container configuration
        String buildServiceName = "Testcontainers__" + containerClass.getName() + "__" + dockerImageName;
        Provider<TestcontainersBuildService> buildServiceProvider = project.getGradle().getSharedServices().registerIfAbsent(buildServiceName, TestcontainersBuildService.class, spec -> {
            TestcontainersBuildService.Params parameters = spec.getParameters();
            parameters.getContainerClassName().set(containerClass.getName());
            parameters.getDockerImageName().set(dockerImageName);

            // We actually share the build service among all tasks that require the same container configuration instead
            // of providing each task with its own container instance, because spinning up containers is time-consuming.
            // We must ensure that tasks using the same container instance cannot run in parallel.
            spec.getMaxParallelUsages().set(1);
        });

        // Configure the task to use the build service and execute the callback action in a doFirst action
        taskProvider.configure(task -> {
            task.usesService(buildServiceProvider);

            // Image tag might come from gradle.properties, ensure that up-to-date checks consider it as an input
            task.getInputs().property("Testcontainers_" + containerDescription + "_image", dockerImageName);

            // noinspection Convert2Lambda (cannot use lambda expression here due to limitation by Gradle)
            task.doFirst(new Action<>() {
                @Override
                public void execute(Task /* ignore this parameter and use variable 'task' with correct type */ ignored) {
                    task.getLogger().info("Task '{}' requests Docker container '{}' from build service '{}'", task.getPath(), dockerImageName, buildServiceName);
                    TestcontainersBuildService buildService = buildServiceProvider.get();
                    @SuppressWarnings("unchecked")
                    CONTAINER container = (CONTAINER) buildService.container;
                    boolean isFirstUse = buildService.isFresh.getAndSet(false);
                    containerStartedCallback.accept(new CallbackParameters<>(task, container, isFirstUse));
                }
            });
        });
    }

    @Slf4j
    public static abstract class TestcontainersBuildService implements BuildService<TestcontainersBuildService.Params>, AutoCloseable {
        public interface Params extends BuildServiceParameters {
            // Name of Testcontainers container class to use. String instead of Class because the latter cannot be
            // serialized by Gradle's configuration cache.
            Property<String> getContainerClassName();

            // Name (including version) of the Docker image to use. String instead of DockerImageName because the latter
            // cannot be serialized by Gradle's configuration cache.
            Property<String> getDockerImageName();
        }

        private final String dockerImageName;
        private final GenericContainer<?> container;
        private final AtomicBoolean isFresh = new AtomicBoolean(true);

        @SuppressWarnings("this-escape")
        public TestcontainersBuildService() throws ReflectiveOperationException {
            Params parameters = getParameters();
            @SuppressWarnings("rawtypes")
            Class<? extends GenericContainer> containerClass = Class.forName(parameters.getContainerClassName().get()).asSubclass(GenericContainer.class);
            dockerImageName = parameters.getDockerImageName().get();
            DockerImageName parsedDockerImageName = DockerImageName.parse(dockerImageName);
            log.info("Starting Docker container '{}'...", dockerImageName);
            container = containerClass.getDeclaredConstructor(DockerImageName.class).newInstance(parsedDockerImageName);

            // Configure logging of container output
            Logger dockerLogger = LoggerFactory.getLogger("docker.[" + parsedDockerImageName.getUnversionedPart() + "].[" + parsedDockerImageName.getVersionPart() + "]");
            Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(dockerLogger);
            if (!dockerLogger.isDebugEnabled()) {
                // Gradle includes the logger name only in debug mode. For lower log levels, we add a prefix such that
                // it is clear where the log output comes from.
                logConsumer.withPrefix(("UTF-8".equals(System.getProperty("file.encoding")) ? "\ud83d\udc33 " : "Docker: ") + dockerImageName);
            }
            container.withLogConsumer(logConsumer);

            // Start the container
            this.container.start();
            log.info("Started Docker container '{}'.", dockerImageName);
        }

        @Override
        public void close() {
            log.info("Stopping Docker container '{}'...", dockerImageName);
            container.stop();
            log.info("Stopped Docker container '{}'.", dockerImageName);
        }
    }
}
