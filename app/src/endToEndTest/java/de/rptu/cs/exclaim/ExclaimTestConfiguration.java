package de.rptu.cs.exclaim;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import de.rptu.cs.exclaim.data.records.UserRecord;
import de.rptu.cs.exclaim.security.ExclaimAuthentication;
import de.rptu.cs.exclaim.security.ExclaimUserPrincipal;
import de.rptu.cs.exclaim.security.SecurityConfig.PublicPath;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static de.rptu.cs.exclaim.schema.tables.Users.USERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
    // Specify the application class to start
    classes = ExclaimApplication.class,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,

    // Start webserver, port is defined below in EnvironmentInitializer
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,

    // Set test-specific properties, more are defined below in EnvironmentInitializer
    properties = {
        // Ignore application.properties files outside the classpath
        "spring.config.location=classpath:/",
        // Do not print the banner
        "spring.main.banner-mode=off"
    }
)

// Tell the Spring Framework about our ApplicationContextInitializer
@ContextConfiguration(initializers = ExclaimTestConfiguration.EnvironmentInitializer.class)

// We need to explicitly import components declared within this class, they are not detected by classpath scanning.
@Import({
    ExclaimTestConfiguration.TestFlywayMigrationStrategy.class,
    ExclaimTestConfiguration.TestLoginController.class,
    ExclaimTestConfiguration.TestSessionListener.class,
})

// Activate our JUnit extension that cleans the database between tests
@ExtendWith(ExclaimTestConfiguration.FlywayCleanExtension.class)

// Spring (instead of JUnit) is responsible for setting constructor parameters of test classes
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)

// Use the same instance of the test class for executing all contained test methods
@TestInstance(value = TestInstance.Lifecycle.PER_CLASS)

@Slf4j
public abstract class ExclaimTestConfiguration {
    // -----------------------------------------------------------------------------------------------------------------
    // Load properties (passed from the Gradle build), select an available port, start the Selenium WebDriver,
    // and start an SMTP mail server to receive mails during our tests.

    private static final String JDBC_URL;
    private static final String JDBC_USERNAME;
    private static final String JDBC_PASSWORD;
    private static final String SELENIUM_HOST_ADDRESS;
    private static boolean databaseIsClean;
    private static final int httpPort;
    private static final String publicUrl;

    protected static final WebDriver driver;
    protected static final WebDriverWait wait;
    protected static final GreenMail greenMail;

    static {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        // Load properties
        String seleniumUrl, seleniumCapabilitiesClassName;
        {
            Properties properties = System.getProperties();
            JDBC_URL = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_url"));
            JDBC_USERNAME = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_username"));
            JDBC_PASSWORD = Objects.requireNonNull(properties.getProperty("exclaim.jdbc_password"));
            SELENIUM_HOST_ADDRESS = Objects.requireNonNull(properties.getProperty("exclaim.selenium_host_address"));
            seleniumUrl = Objects.requireNonNull(properties.getProperty("exclaim.selenium_url"));
            seleniumCapabilitiesClassName = Objects.requireNonNull(properties.getProperty("exclaim.selenium_capabilities_class"));
            databaseIsClean = Boolean.parseBoolean(properties.getProperty("exclaim.is_fresh_database"));
        }

        // Select http server port
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            httpPort = serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not select http server port", e);
        }
        assertThat("Invalid http server port", httpPort, greaterThan(0));
        publicUrl = UriComponentsBuilder.newInstance().scheme("http").host(SELENIUM_HOST_ADDRESS).port(httpPort).build().toString();

        // Selenium WebDriver
        log.info("Creating Selenium WebDriver for {}", seleniumUrl);
        try {
            driver = new RemoteWebDriver(new URI(seleniumUrl).toURL(), Class.forName(seleniumCapabilitiesClassName).asSubclass(Capabilities.class).getDeclaredConstructor().newInstance(), false);
            log.info("Browser has started");
        } catch (URISyntaxException | MalformedURLException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Closing Selenium WebDriver...");
            driver.quit();
            log.info("Closed Selenium WebDriver.");
        }, "WebDriver-Shutdown-Hook"));
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        // SMTP mail server
        log.info("Starting SMTP server...");
        greenMail = new GreenMail(new ServerSetup(0, null, ServerSetup.PROTOCOL_SMTP));
        greenMail.start();
        log.info("SMTP server has started.");
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Initialize the Spring Application:
    // - set environment properties (data source, web server port, mail server)
    // - if we started with a potentially dirty database, clean it before running migrations

    @Order(Ordered.HIGHEST_PRECEDENCE) // before our SpringDatasourceUrlCustomizer for PostgreSQL
    public static class EnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            // We use SpringBootTest.WebEnvironment.DEFINED_PORT and select a random port ourselves (above in the static
            // block), because the "exclaim.public-url" property depends on it. With WebEnvironment.RANDOM_PORT, there
            // is no way to get the selected port until it is too late to set change the environment. Furthermore, we
            // cannot set these properties inside @SpringBootTest(properties = ...) because they are not constant
            // expressions (limitation by the Java compiler).
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("ExclaimTestConfiguration", Map.of(
                "server.port", httpPort,
                "exclaim.public-url", publicUrl,
                "spring.datasource.url", JDBC_URL,
                "spring.datasource.username", JDBC_USERNAME,
                "spring.datasource.password", JDBC_PASSWORD,
                "spring.mail.port", greenMail.getSmtp().getPort(),
                // do not persist sessions during testing
                "server.servlet.session.persistent", false,
                // allow Flyway clean (used between tests)
                "spring.flyway.clean-disabled", false
            )));
            ConfigurationPropertySources.attach(environment);
        }
    }

    @Component
    public static class TestFlywayMigrationStrategy implements FlywayMigrationStrategy {
        @Override
        public void migrate(Flyway flyway) {
            if (!databaseIsClean) {
                log.info("Started with a potentially dirty database, running Flyway clean before migrate...");
                flyway.clean();
                databaseIsClean = true;
            }
            flyway.migrate();
        }
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Utility methods to be used in tests

    protected static String url() {
        return publicUrl;
    }

    protected static String url(String path) {
        if (path.isEmpty() || path.equals("/")) {
            return publicUrl;
        }
        return path.charAt(0) == '/'
            ? publicUrl + path
            : publicUrl + "/" + path;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Bypass the login form and directly authenticate the session for a given user. This is done by pointing the
    // browser to a special URL where a controller authenticates the user without any checks. That controller is only
    // available for testing, of course, since it is in the endToEndTest source set.

    private static final String LOGIN_FOR_TEST_PATH = "/login-for-test";

    protected static void login(int userId) {
        log.info("Attempting to log in user id {}...", userId);
        driver.get(url(LOGIN_FOR_TEST_PATH + "?userId=" + userId));
        String currentUrl = driver.getCurrentUrl();
        assertEquals(publicUrl + "/", currentUrl, "Login via test method did not succeed!");
    }

    @Controller
    @RequiredArgsConstructor
    public static class TestLoginController {
        private final DSLContext ctx;

        @GetMapping(LOGIN_FOR_TEST_PATH)
        public String loginForTest(@RequestParam int userId) {
            log.info("Received attempt to log in user id {}...", userId);
            UserRecord userRecord = ctx.fetchSingle(USERS, USERS.USERID.eq(userId));
            SecurityContextHolder.getContext().setAuthentication(
                new ExclaimAuthentication(new ExclaimUserPrincipal(userRecord))
            );
            log.info("Authenticated the current session for {}", userRecord);
            return "redirect:/";
        }

        @Bean
        public static PublicPath loginForTestPath() {
            return new PublicPath(LOGIN_FOR_TEST_PATH);
        }
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Clean all client-side (browser cookies) and server-side (session, e-mails) information after each test

    private static final Set<HttpSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Component
    public static class TestSessionListener implements HttpSessionListener {
        @Override
        public void sessionCreated(HttpSessionEvent event) {
            sessions.add(event.getSession());
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event) {
            sessions.remove(event.getSession());
        }
    }

    @AfterEach
    public void cleanup() throws FolderException {
        if (log.isInfoEnabled()) {
            log.info("Cleaning browser cookies: {}", driver.manage().getCookies());
        }
        driver.manage().deleteAllCookies();

        if (log.isInfoEnabled()) {
            log.info("Cleaning server sessions: {}", sessions.stream().map(HttpSession::getId).toList());
        }
        sessions.forEach(HttpSession::invalidate);

        log.info("Cleaning received mails...");
        greenMail.purgeEmailFromAllMailboxes();
    }


    // -----------------------------------------------------------------------------------------------------------------
    // JUnit extension that handles the following annotations on test methods:
    // - @RequiresCleanDatabase: Indicates that the test method relies on a clean database state
    // - @MutatesDatabase: Indicates that the test method leaves the database in a dirty state

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface RequiresCleanDatabase {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MutatesDatabase {
    }

    @Autowired
    private Flyway flyway;

    public static class FlywayCleanExtension implements BeforeEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            Method testMethod = context.getTestMethod().orElseThrow();
            String name = context.getTestClass().map(c -> c.getSimpleName() + ".").orElse("") + context.getDisplayName();
            if (testMethod.isAnnotationPresent(RequiresCleanDatabase.class)) {
                if (databaseIsClean) {
                    log.info("Test {} requires a clean database, and the database is clean.", name);
                } else {
                    log.info("Test {} requires a clean database, but the database is dirty. Running flyway clean and migrate...", name);
                    Flyway flyway = ((ExclaimTestConfiguration) context.getTestInstance().orElseThrow()).flyway;
                    flyway.clean();
                    flyway.migrate();
                    log.info("Database cleaned.");
                    databaseIsClean = true;
                }
            }
            if (testMethod.isAnnotationPresent(MutatesDatabase.class)) {
                log.info("Test {} mutates the database, marking as dirty.", name);
                databaseIsClean = false;
            }
        }
    }
}
