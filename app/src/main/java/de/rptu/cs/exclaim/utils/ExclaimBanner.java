package de.rptu.cs.exclaim.utils;

import de.rptu.cs.exclaim.Main;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.Banner;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The banner to print on application startup.
 * <p>
 * Shows a console replica of our logo, version information and the URL to access the application.
 */
public class ExclaimBanner implements Banner {
    private static final String UNKNOWN = "unknown";

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        // Logo
        out.println();
        printLogo(out);
        out.println();

        // Version
        printVersion(detectVersion(sourceClass), out);

        // Database variant and URL
        printDatabase(detectDatabaseVariant(sourceClass), detectDatabaseUrl(environment), out);

        // Application URL
        if (!Main.getShutdown()) {
            printApplicationUrl(detectApplicationUrl(environment), out);
        }

        out.println();
    }

    /**
     * Use <a href="https://en.wikipedia.org/wiki/Code_page_437">code page 437</a> characters and
     * <a href="https://en.wikipedia.org/wiki/ANSI_escape_code">ansi colors</a> to create a beautiful console replica of
     * our logo. For the checkmark we use not only a different ansi color but also a different shade (other character)
     * such that it looks different also in non-ansi mode.
     *
     * @param out the output print stream
     */
    private void printLogo(PrintStream out) {
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "███████  ██    ██    ██████   ", AnsiColor.GREEN, "▒▒", AnsiColor.BLUE, "██       ██    ██  ██      ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "███████  ██   ███  █████████ ", AnsiColor.GREEN, "▒▒▒", AnsiColor.BLUE, "██      ███    ██  ███    ███"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██       ███ ███   ███    ██", AnsiColor.GREEN, "▒▒▒", AnsiColor.BLUE, " ██      ███    ██  ███    ███"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██        █████   ███ ", AnsiColor.GREEN, "▒▒▒  ▒▒▒", AnsiColor.BLUE, "  ██      ████   ██  ████  ████"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██         ████   ██  ", AnsiColor.GREEN, "▒▒▒ ▒▒▒", AnsiColor.BLUE, "   ██     ██ ██   ██  ████  ████"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██████     ███    ██  ", AnsiColor.GREEN, "▒▒▒ ▒▒▒", AnsiColor.BLUE, "   ██     ██ ██   ██  ███████ ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██         ████   ██  ", AnsiColor.GREEN, "▒▒▒▒▒▒", AnsiColor.BLUE, "    ██     ██████  ██  ██ ████ ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██        █████   ███ ", AnsiColor.GREEN, "▒▒▒▒▒", AnsiColor.BLUE, "     ██    ███████  ██  ██  ██  ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "██       ███ ███   ███ ", AnsiColor.GREEN, "▒▒", AnsiColor.BLUE, " ███   ██    ██   ███ ██  ██  ██  ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "███████  ██   ███  █████████    ████████    ██ ██  ██      ██"));
        out.println(AnsiOutput.toString(AnsiColor.BLUE, "███████ ███    ██    ██████     ███████     ██ ██  ██      ██"));
    }

    /**
     * Print version information. We try to parse the version as <a href="https://semver.org/">SemVer</a> and use
     * different colors for the components.
     *
     * @param version the version to print, or null if unknown
     * @param out     the output print stream
     */
    private void printVersion(@Nullable String version, PrintStream out) {
        // Build output line in ArrayList, provide maximum possible size for optimization
        List<Object> ansiOutput = new ArrayList<>(14);

        ansiOutput.addAll(List.of(AnsiStyle.BOLD, AnsiColor.BLUE, "Version: ", AnsiStyle.NORMAL));

        if (version != null) {
            ansiOutput.add(AnsiColor.GREEN);

            // Parse SemVer
            Matcher matcher = Pattern.compile("([^-+]+)(?:-([^+]+))?(?:\\+(.+))?").matcher(version);
            if (matcher.matches()) {
                // Part before first - or + (should be: Major.Minor.Patch)
                ansiOutput.add(matcher.group(1));

                // Part after first - up to first + (pre-release version)
                String preRelease = matcher.group(2);
                if (preRelease != null) {
                    ansiOutput.addAll(List.of(AnsiColor.BLUE, "-", AnsiColor.RED, preRelease));
                }

                // Part after first + (build metadata, e.g. commit hash)
                String buildMetadata = matcher.group(3);
                if (buildMetadata != null) {
                    ansiOutput.addAll(List.of(AnsiColor.BLUE, "+", AnsiColor.MAGENTA, buildMetadata));
                }
            } else {
                // Non-SemVer version
                ansiOutput.add(version);
            }
        } else {
            // Unknown version
            ansiOutput.addAll(List.of(AnsiColor.RED, UNKNOWN));
        }

        out.println(AnsiOutput.toString(ansiOutput.toArray()));
    }

    /**
     * Print database variant and URL.
     *
     * @param variant the database variant to print, or null if unknown
     * @param url     the database URL to print, or null if unknown
     * @param out     the output print stream
     */
    private void printDatabase(@Nullable String variant, @Nullable String url, PrintStream out) {
        // Build output line in ArrayList, provide maximum possible size for optimization
        List<Object> ansiOutput = new ArrayList<>(14);

        ansiOutput.addAll(List.of(AnsiStyle.BOLD, AnsiColor.BLUE, "Database: ", AnsiStyle.NORMAL));

        // Print database variant
        if (variant != null) {
            ansiOutput.addAll(List.of(AnsiColor.GREEN, variant));
        } else {
            ansiOutput.addAll(List.of(AnsiColor.RED, UNKNOWN + " variant"));
        }

        ansiOutput.addAll(List.of(AnsiColor.BLUE, " / "));

        // Print database url, but redact passwords
        if (url != null) {
            ansiOutput.add(AnsiColor.GREEN);
            int startIndex = 0;
            Matcher matcher = Pattern.compile("(password=)[^;&]+", Pattern.CASE_INSENSITIVE).matcher(url);
            while (matcher.find()) {
                ansiOutput.addAll(List.of(
                    url.substring(startIndex, matcher.start()) + matcher.group(1),
                    AnsiColor.RED, "********", AnsiColor.GREEN
                ));
                startIndex = matcher.end();
            }
            ansiOutput.add(url.substring(startIndex));
        } else {
            ansiOutput.addAll(List.of(AnsiColor.RED, UNKNOWN + " url"));
        }

        out.println(AnsiOutput.toString(ansiOutput.toArray()));
    }

    /**
     * Print the application URL.
     *
     * @param url application URL to print, or null if unknown
     * @param out the output print stream
     */
    private void printApplicationUrl(@Nullable String url, PrintStream out) {
        out.println(AnsiOutput.toString(
            AnsiStyle.BOLD, AnsiColor.BLUE, "Application URL: ", AnsiStyle.NORMAL,
            url == null ? AnsiColor.RED : AnsiColor.GREEN, url == null ? UNKNOWN : url
        ));
    }

    /**
     * Detect the application version.
     *
     * @param sourceClass the source class for the application
     * @return the application version, or null if unknown
     */
    @Nullable
    private String detectVersion(Class<?> sourceClass) {
        // When running from jar file, we read the META-INF/MANIFEST.MF standard key "Implementation-Version"
        String version = sourceClass.getPackage().getImplementationVersion();
        if (StringUtils.isNotEmpty(version)) {
            return version;
        }

        // Fallback for bootRun: Load version from system property
        return StringUtils.defaultIfEmpty(System.getProperty("exclaim.version"), null);
    }

    /**
     * Detect the database variant that the application has been built for.
     *
     * @param sourceClass the source class for the application
     * @return the database variant that the application has been built for, or null if unknown
     */
    @Nullable
    private String detectDatabaseVariant(Class<?> sourceClass) {
        // When running from jar file, we read our custom key "Implementation-Version" from META-INF/MANIFEST.MF
        try (InputStream is = new ClassPathResource("META-INF/MANIFEST.MF", sourceClass.getClassLoader()).getInputStream()) {
            Properties properties = new Properties();
            properties.load(is);
            Object databaseVariant = properties.get("Implementation-Variant");
            if (databaseVariant != null) {
                String databaseVariantString = databaseVariant.toString();
                if (!databaseVariantString.isEmpty()) {
                    return databaseVariantString;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }

        // Fallback for bootRun: Load variant from system property
        return StringUtils.defaultIfEmpty(System.getProperty("exclaim.variant"), null);
    }

    /**
     * Detect the database URL from application properties in the provided environment.
     *
     * @param environment the spring environment
     * @return the database URL, or null if unknown
     */
    @Nullable
    private String detectDatabaseUrl(Environment environment) {
        return environment.getProperty("spring.datasource.url");
    }

    /**
     * Detect the application URL from application properties in the provided environment.
     *
     * @param environment the spring environment
     * @return the application URL
     */
    private String detectApplicationUrl(Environment environment) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();

        // Detect scheme
        boolean ssl = environment.getProperty("server.ssl.enabled", Boolean.class, true) && (
            StringUtils.isNotEmpty(environment.getProperty("server.ssl.certificate-private-key"))
                || StringUtils.isNotEmpty(environment.getProperty("server.ssl.bundle"))
        );
        uriBuilder.scheme(ssl ? "https" : "http");

        // Detect host
        InetAddress address = environment.getProperty("server.address", InetAddress.class);
        uriBuilder.host(address == null || address.isAnyLocalAddress()
            ? "localhost"
            : address.getHostAddress()
        );

        // Detect port
        int port = environment.getProperty("server.port", Integer.class, 8080);
        if ((!ssl && port != 80) || (ssl && port != 443)) {
            uriBuilder.port(port);
        }

        // Detect path
        String contextPath = environment.getProperty("server.servlet.context-path");
        if (StringUtils.isNotEmpty(contextPath)) {
            uriBuilder.path(contextPath);
        }

        return uriBuilder.toUriString();
    }
}
