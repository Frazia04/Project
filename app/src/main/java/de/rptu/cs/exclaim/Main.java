package de.rptu.cs.exclaim;

import de.rptu.cs.exclaim.Main.Admin;
import de.rptu.cs.exclaim.Main.Generate;
import lombok.Getter;
import lombok.ToString;
import org.springframework.lang.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.RunAll;
import picocli.CommandLine.ScopeType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This is the main entry point into the executable application.
 * <p>
 * It parses the command line options and then executes {@link ExclaimApplication#main} to start the Spring application.
 * This way, <code>--help</code> and invalid options can be handled before the application starts.
 * <p>
 * The actual work for executing the requested CLI options needs to be done by runners in the
 * {@link de.rptu.cs.exclaim.runners} package, because nothing has been initialized yet at this point.
 * <p>
 * If your IDE complains that this class is not a Spring application then you can just use {@link ExclaimApplication},
 * but passing CLI options via your IDE run configuration will not work.
 * Use <code>./gradlew bootRun --args='...'</code> instead.
 */
@Command(name = "exclaim.jar",
    subcommands = {Admin.class, Generate.class, Main.Shutdown.class},
    subcommandsRepeatable = true)
public class Main implements Runnable {
    public static void main(String[] args) {
        if (args.length == 0) {
            // No args given, just launch the Spring application
            ExclaimApplication.main(args);
        } else {
            // Parse CLI options
            Main main = new Main();
            CommandLine cmd = new CommandLine(main);
            cmd.setAllowOptionsAsOptionParameters(true);
            cmd.setAllowSubcommandsAsOptionParameters(true);
            cmd.setExecutionStrategy(new RunAll());

            // Execute the run() methods of all requested sub commands
            int exitCode = cmd.execute(args);
            if (main.run) {
                // The application shall start. Disable devtools restart, since that would instantiate a new class
                // loader and thus not reveal our static fields to the application.
                System.setProperty("spring.devtools.restart.enabled", "false");
                ExclaimApplication.main(args);
            } else {
                // Illegal options or just --help, exit with an appropriate exit code
                System.exit(exitCode);
            }
        }
    }

    // Reader required for querying missing arguments from STDIN.
    @Nullable
    private BufferedReader reader;

    private static BufferedReader getReader(Main main) {
        if (main.reader == null) {
            main.reader = new BufferedReader(new InputStreamReader(System.in, UTF_8));
        }
        return main.reader;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Top Level Command

    // Whether the provided CLI arguments allow starting the application
    private boolean run = false;

    @Override
    public void run() {
        // This run method is called before those of the sub commands, therefore we just set the flag and delegate
        // starting the application to the main method.
        run = true;
    }

    @Option(names = {"-h", "--help"}, usageHelp = true, hidden = true, scope = ScopeType.INHERIT)
    @SuppressWarnings("UnusedVariable")
    private boolean helpRequested = false;

    @Nullable
    @Option(names = "--spring.config.location", hidden = true)
    @SuppressWarnings("UnusedVariable")
    private String springConfigLocation;

    @Nullable
    @Option(names = "--spring.config.name", hidden = true)
    @SuppressWarnings("UnusedVariable")
    private String springConfigName;


    // -----------------------------------------------------------------------------------------------------------------
    // Admin

    public static final List<Admin> admin = new ArrayList<>();

    @Getter
    @ToString
    @Command(name = "admin",
        description = "Create a new user account with admin permissions.")
    public static class Admin implements Callable<Integer> {
        @Option(names = {"-u"}, description = "The username for the new account. Defaults to 'admin'.")
        private String username = "admin";

        @Option(names = {"-p"}, description = "The password to use for the new account. If not provided, the password will be queried from the console.")
        private String password;

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            if (main.run) {
                // Validate and sanitize username
                username = username.trim();
                if (username.isEmpty()) {
                    username = "admin";
                }

                // Validate and sanitize password, potentially requesting one on stdin.
                if (password != null) {
                    password = password.trim();
                }
                if (password == null || password.isEmpty()) {
                    System.out.printf("Enter the password for the new admin account with username '%s': ", username);
                    try {
                        password = getReader(main).readLine();
                        if (password != null) {
                            password = password.trim();
                        }
                    } catch (IOException ignored) { // handled in next if clause
                    }
                    if (password == null || password.isEmpty()) {
                        System.err.printf("Missing password for the new admin account with username '%s'!%n", username);
                        main.run = false;
                        return 1;
                    }
                }
                password = password.trim();
                admin.add(this);
            }
            return 0;
        }
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Generate

    public static final List<Generate> generate = new ArrayList<>();

    @Getter
    @ToString
    @Command(name = "generate",
        description = "Generate some fake data with random names.")
    public static class Generate implements Runnable {
        @Override
        public void run() {
            generate.add(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Shutdown

    @Getter
    private static boolean shutdown = false;

    @Command(name = "shutdown",
        description = "Run database migrations and CLI commands, then terminate.")
    public static class Shutdown implements Runnable {
        @Override
        public void run() {
            shutdown = true;
        }
    }
}
