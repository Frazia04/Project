package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.JavaExec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java Exec Output Logger
 * <p>
 * For {@link JavaExec} tasks that write their own logs to the standard output, we want to redirect log messages to
 * Gradle's logging system, keeping the original log level and logger name.
 * <p>
 * We assume that log lines have the following Logback format pattern:
 * <pre>
 *   [%level] [[%logger]]: %msg%n
 * </pre>
 */
public class JavaExecOutputLogger {
    // Pattern to detect the log level in a line
    private static final Pattern LOG_PATTERN = Pattern.compile("\\[(DEBUG|INFO|WARN|ERROR)] \\[\\[([^]]*)]]: (.*)");

    // Names of the system property provided to the task
    private static final String LOG_LEVEL_PROPERTY_NAME = "exclaim.log_level";

    public static void applyToSingleThreadedTask(JavaExec task) {
        // noinspection Convert2Lambda (cannot use lambda expression here due to limitation by Gradle)
        task.doFirst(new Action<>() {
            @Override
            public void execute(Task /* ignore this parameter and use variable 'task' with correct type */ ignored) {
                // We must set the standard output at execution time (in this doFirst action) instead of at
                // configuration time because we otherwise would break Gradle's configuration cache.
                task.setStandardOutput(new LoggingOutputStream(task.getLogger()));

                // Add a system property with the log level, such that the logger instance in the JavaExec execution can
                // avoid unnecessary work printing muted messages. Assign the property at execution time such that it is
                // not considered for Gradle's up-to-date checks.
                task.systemProperty(LOG_LEVEL_PROPERTY_NAME, Utils.detectSlf4jLogLevel(task.getLogger()));
            }
        });
    }

    public static void applyToMultithreadedTask(JavaExec task) {
        // noinspection Convert2Lambda (cannot use lambda expression here due to limitation by Gradle)
        task.doFirst(new Action<>() {
            @Override
            public void execute(Task /* ignore this parameter and use variable 'task' with correct type */ ignored) {
                task.setStandardOutput(new ThreadSafeLoggingOutputStream(task.getLogger()));
                task.systemProperty(LOG_LEVEL_PROPERTY_NAME, Utils.detectSlf4jLogLevel(task.getLogger()));
            }
        });
    }

    // Only provide the log level without parsing the standard output
    public static void provideLogLevel(JavaExec task) {
        // noinspection Convert2Lambda (cannot use lambda expression here due to limitation by Gradle)
        task.doFirst(new Action<>() {
            @Override
            public void execute(Task /* ignore this parameter and use variable 'task' with correct type */ ignored) {
                task.systemProperty(LOG_LEVEL_PROPERTY_NAME, Utils.detectSlf4jLogLevel(task.getLogger()));
            }
        });
    }

    private static class LoggingOutputStream extends OutputStream {
        // The logger that should receive log lines written to this output stream
        private Logger logger;

        // A buffer where we collect output until we reach the end of a line.
        // size denotes the currently used length of that buffer.
        private final byte[] buffer = new byte[16384]; // support lines with up to 16kb
        private int size = 0;

        // Log level of the previous line. This is required for multi-line log messages.
        private LogLevel logLevel = LogLevel.QUIET; // start with Gradle's default log level for stdout

        private LoggingOutputStream(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void write(int i) throws IOException {
            // Specification in the OutputStream interface:
            // The byte to be written is the eight low-order bits of the argument b.
            // The 24 high-order bits of b are ignored.
            byte b = (byte) (i & 255);

            if (b == '\n') {
                sendLineToLogger();
            } else if (size == buffer.length) {
                throw new IOException("Buffer overflow");
            } else {
                buffer[size++] = b;
            }
        }

        @Override
        public void close() {
            if (size != 0) {
                sendLineToLogger();
            }
        }

        private void sendLineToLogger() {
            // Line has ended with '\n' (not contained in the buffer). Remove '\r' directly preceding the '\n'.
            if (size != 0 && buffer[size - 1] == '\r') {
                --size;
            }

            // Read and remove the line from our buffer
            String line = new String(buffer, 0, size, StandardCharsets.UTF_8);
            size = 0;

            // Parse the log line
            Matcher matcher = LOG_PATTERN.matcher(line);
            if (matcher.matches()) {
                logLevel = LogLevel.valueOf(matcher.group(1));
                logger = Logging.getLogger(matcher.group(2));
                logger.log(logLevel, matcher.group(3));
            } else {
                // Log the line using the last known logger and log level
                logger.log(logLevel, line);
            }
        }
    }

    private static class ThreadSafeLoggingOutputStream extends LoggingOutputStream {
        private ThreadSafeLoggingOutputStream(Logger logger) {
            super(logger);
        }

        @Override
        public synchronized void write(int i) throws IOException {
            super.write(i);
        }

        @Override
        public synchronized void write(byte[] b) throws IOException {
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
        }

        @Override
        public synchronized void close() {
            super.close();
        }
    }
}
