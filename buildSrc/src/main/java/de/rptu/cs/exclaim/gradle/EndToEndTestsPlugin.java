package de.rptu.cs.exclaim.gradle;

import lombok.RequiredArgsConstructor;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.rptu.cs.exclaim.gradle.SqlDialectSourceSetsIncludeMainPlugin.dialectSourceSetsIncludeBaseSourceSet;
import static de.rptu.cs.exclaim.gradle.SqlDialectTestSourceSetsPlugin.registerDialectTestSourceSets;
import static de.rptu.cs.exclaim.gradle.SqlDialectTestSourceSetsPlugin.registerDialectTestTasks;
import static de.rptu.cs.exclaim.gradle.TestcontainersUtils.addTestcontainersContainerToTask;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * End-To-End Tests Plugin
 * <p>
 * This plugin adds separate end-to-end test tasks for each combination of supported SQL dialect and browser. These test
 * tasks have access to a database server and a Selenium browser.
 */
public class EndToEndTestsPlugin implements Plugin<Project> {
    // Name of the test suite
    private static final String END_TO_END_TEST_NAME = "endToEndTest";

    // Selenium port in Docker images
    private static final int SELENIUM_PORT = 4444;

    // Hostname to be used inside the docker container to reach the host running our build
    private static final String DOCKER_HOST_ADDRESS = "host.docker.internal";

    // Name of system properties provided to the test task
    private static final String SELENIUM_URL_PROPERTY_NAME = "exclaim.selenium_url";
    private static final String SELENIUM_CAPABILITIES_CLASS_PROPERTY_NAME = "exclaim.selenium_capabilities_class";
    private static final String SELENIUM_HOST_ADDRESS_PROPERTY_NAME = "exclaim.selenium_host_address";

    // Name of environment variables that can be provided to the build
    // URL to Selenium instance: SELENIUM_CHROME_URL, SELENIUM_FIREFOX_URL
    private static final String SELENIUM_HOST_ADDRESS_ENVIRONMENT_VARIABLE_NAME = "SELENIUM_HOST_ADDRESS";

    @RequiredArgsConstructor
    public static class BrowserInformation {
        // Human-readably name for the browser, used also in names of environment variables and keys in gradle.properties
        public final String name;

        // Runtime dependency for this browser's WebDriver
        public final String driverDependency;

        // Capabilities class name for this browser's WebDriver
        public final String capabilitiesClass;

        // Docker image to use
        public final String image;
    }

    public static final List<BrowserInformation> BROWSERS = List.of(
        new BrowserInformation(
            "Chrome",
            "org.seleniumhq.selenium:selenium-chrome-driver",
            "org.openqa.selenium.chrome.ChromeOptions",
            "selenium/standalone-chrome"
        ),
        new BrowserInformation(
            "Firefox",
            "org.seleniumhq.selenium:selenium-firefox-driver",
            "org.openqa.selenium.firefox.FirefoxOptions",
            "selenium/standalone-firefox"
        )
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SqlDialectSourceSetsPlugin.class);
        project.getPluginManager().apply(JUnitPlugin.class);
        project.getPluginManager().apply(JacocoAggregateReportPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
        String seleniumVersion = (String) extraProperties.get("selenium_version");

        // Register a source set serving as common base for the dialect-specific test suites.
        sourceSets.register(END_TO_END_TEST_NAME, endToEndTestSourceSet -> {
            // Extend from test dependencies
            project.getConfigurations().named(endToEndTestSourceSet.getImplementationConfigurationName()).configure(c ->
                c.extendsFrom(project.getConfigurations().getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME))
            );
            project.getConfigurations().named(endToEndTestSourceSet.getRuntimeOnlyConfigurationName()).configure(c ->
                c.extendsFrom(project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME))
            );

            // Add Selenium dependency
            project.getDependencies().add(endToEndTestSourceSet.getImplementationConfigurationName(), "org.seleniumhq.selenium:selenium-remote-driver:" + seleniumVersion + "!!");
            project.getDependencies().add(endToEndTestSourceSet.getImplementationConfigurationName(), "org.seleniumhq.selenium:selenium-support:" + seleniumVersion + "!!");

            // IntelliJ IDEA: Mark source set as tests
            project.getExtensions().getByType(IdeaModel.class).module(ideaModule -> {
                ideaModule.getTestSources().from(endToEndTestSourceSet.getAllJava().getSrcDirs());
                ideaModule.getTestResources().from(endToEndTestSourceSet.getResources().getSrcDirs());
            });
        });

        // Register dialect-specific test source sets
        registerDialectTestSourceSets(project, END_TO_END_TEST_NAME, EndToEndTestsPlugin::sourceSetName);

        // Let the dialect-specific end-to-end test source set include the base end-to-end test source set
        dialectSourceSetsIncludeBaseSourceSet(project, END_TO_END_TEST_NAME, EndToEndTestsPlugin::sourceSetName);

        for (BrowserInformation browser : BROWSERS) {
            // Add a configuration holding the runtime dependency for this browsers Selenium driver
            NamedDomainObjectProvider<Configuration> browserRuntimeClasspathConfiguration = project.getConfigurations().register(END_TO_END_TEST_NAME + browser.name + "RuntimeClasspath", c -> {
                c.setDescription("Selenium WebDriver dependencies for the " + browser.name + " browser.");
                c.setVisible(false);
                c.setCanBeDeclared(true);
                c.setCanBeResolved(true);
                c.setCanBeConsumed(false);
                c.getDependencies().add(project.getDependencies().create(browser.driverDependency + ":" + seleniumVersion + "!!"));
            });

            // Register dialect-specific test tasks for this browser
            registerDialectTestTasks(project, EndToEndTestsPlugin::sourceSetName, dialect -> testTaskName(dialect, browser.name), (testTaskProvider, dialect) -> {
                testTaskProvider.configure(testTask -> {
                    testTask.setDescription("Runs the end-to-end tests for the " + dialect + " dialect with a " + browser.name + " browser.");
                    testTask.shouldRunAfter(dialect.getTestSourceSetName()); // run end-to-end tests after normal tests

                    // Add the Selenium driver dependency
                    testTask.setClasspath(testTask.getClasspath().plus(browserRuntimeClasspathConfiguration.get()));
                    testTask.getSystemProperties().put(SELENIUM_CAPABILITIES_CLASS_PROPERTY_NAME, browser.capabilitiesClass);
                });

                // Provide a database server
                DatabaseServerProvider.applyTo(project, testTaskProvider, dialect);

                // Provide a Selenium instance
                Map<String, String> env = System.getenv();
                String seleniumUrl = env.get("SELENIUM_" + browser.name.toUpperCase(Locale.ROOT) + "_URL");
                if (seleniumUrl != null && !seleniumUrl.isEmpty()) {
                    // Use external Selenium instance. We register a dummy build service serving as mutex, such that
                    // tasks using the same external Selenium instance cannot run in parallel.
                    Provider<DummyBuildService> buildServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("ExternalSeleniumServer", DummyBuildService.class, spec ->
                        spec.getMaxParallelUsages().set(1)
                    );

                    testTaskProvider.configure(testTask -> {
                        testTask.getLogger().info("Task '{}' will use an external Selenium instance at {}", testTask.getPath(), seleniumUrl);
                        testTask.getSystemProperties().putAll(Map.of(
                            SELENIUM_URL_PROPERTY_NAME, seleniumUrl,
                            SELENIUM_HOST_ADDRESS_PROPERTY_NAME, env.getOrDefault(SELENIUM_HOST_ADDRESS_ENVIRONMENT_VARIABLE_NAME, "localhost")
                        ));
                        testTask.usesService(buildServiceProvider);
                    });
                } else {
                    // Use Selenium instance managed with Testcontainers
                    testTaskProvider.configure(task -> task.getLogger().info("Task '{}' will use a Selenium instance managed with Testcontainers", task.getPath()));
                    @SuppressWarnings({"rawtypes", "unchecked"}) // need to convert raw to generic type
                    Class<SeleniumContainer<?>> containerClass = (Class) SeleniumContainer.class;
                    String dockerImageName = DockerImageName.parse(browser.image).withTag((String) Objects.requireNonNull(extraProperties.get("selenium_" + browser.name.toLowerCase(Locale.ROOT) + "_docker_tag"))).asCanonicalNameString();
                    // noinspection Convert2Lambda (cannot use lambda expressions here due to limitation by Gradle)
                    addTestcontainersContainerToTask(project, testTaskProvider, "browser", containerClass, dockerImageName, new Consumer<>() {
                        @Override
                        public void accept(TestcontainersUtils.CallbackParameters<Test, SeleniumContainer<?>> parameters) {
                            parameters.task.getSystemProperties().putAll(Map.of(
                                SELENIUM_URL_PROPERTY_NAME, "http://" + parameters.container.getHost() + ":" + parameters.container.getMappedPort(SELENIUM_PORT),
                                SELENIUM_HOST_ADDRESS_PROPERTY_NAME, DOCKER_HOST_ADDRESS
                            ));
                        }
                    });
                }
            });
        }
    }

    private static String sourceSetName(SqlDialect dialect) {
        return END_TO_END_TEST_NAME + dialect.name();
    }

    private static String testTaskName(SqlDialect dialect, String browserName) {
        return END_TO_END_TEST_NAME + dialect.name() + browserName;
    }

    /**
     * This is a slimmed-down variant of the <a href="https://github.com/testcontainers/testcontainers-java/blob/1.17.0/modules/selenium/src/main/java/org/testcontainers/containers/BrowserWebDriverContainer.java">BrowserWebDriverContainer</a> class.
     * <p>
     * We do not need the integration in JUnit and do not have Selenium on the classpath of our build script.
     */
    public static class SeleniumContainer<SELF extends SeleniumContainer<SELF>> extends GenericContainer<SELF> {
        public SeleniumContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
            this.waitStrategy = new LogMessageWaitStrategy()
                .withRegEx(".*Started Selenium Standalone.*\n")
                .withStartupTimeout(Duration.of(30, SECONDS));
        }

        @Override
        protected void configure() {
            addExposedPort(SELENIUM_PORT);
            String timeZone = System.getProperty("user.timezone");
            addEnv("TZ", timeZone == null || timeZone.isEmpty() ? "Etc/UTC" : timeZone);

            // 2 GB, as recommended here: https://github.com/SeleniumHQ/docker-selenium
            setShmSize(2L * 1024L * 1024L * 1024L);

            // On linux, we need to explicitly add host.docker.internal to the /etc/hosts/file
            String osName = System.getProperty("os.name");
            if (osName != null && (osName.startsWith("Linux") || osName.startsWith("LINUX"))) {
                withExtraHost(DOCKER_HOST_ADDRESS, "host-gateway");
            }
        }
    }
}
