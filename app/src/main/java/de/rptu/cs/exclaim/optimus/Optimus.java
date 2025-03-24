package de.rptu.cs.exclaim.optimus;

import de.rptu.cs.exclaim.schema.enums.GroupPreferenceOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class Optimus {
    /**
     * Calculate an optimal group assignment
     *
     * @param groupIds         available groupIds
     * @param groupPreferences map userId -> (groupId -> preference)
     * @param teamPreferences  map userId -> friend userIds
     * @return the calculated assignment (map userId -> groupId)
     */
    public Map<Integer, String> calculateAssignment(
        List<String> groupIds,
        Map<Integer, Map<String, GroupPreferenceOption>> groupPreferences,
        Map<Integer, List<Integer>> teamPreferences
    ) throws IOException, InterruptedException {
        // Implementation note:
        // We use lp_solve, a Linear Programming solver, to do the actual optimization computation.
        // See: https://lpsolve.sourceforge.net/5.5/
        // We do not use lp_solve's native Java API, because deployment and compilation is difficult (native code which
        // needs additional .so/.dll files). Instead, we use the lp_solve binary tool and communicate via stdin/stdout
        // with the process. This approach also avoids issues with lp_solve's LGPL license, because our final jar file
        // does not contain any lp_solve code. The lp_solve binary needs to be installed by the system administrator.

        // Set up our lp_solve model
        LpSolveVariables variables = new LpSolveVariables(groupPreferences.keySet(), groupIds);
        Stream<String> lpSolveLines = new LpSolveFileGenerator(groupIds, groupPreferences, teamPreferences, variables).generateLines();

        // Start the lp_solve process
        // TODO: Make path to executable configurable
        Process process = new ProcessBuilder("lp_solve")
            .redirectErrorStream(true) // merge stderr to stdout
            .start();

        try {
            // Reading and parsing the output from the lp_solve process happens in separate thread
            // such that the main thread does not get blocked when process buffers are full.
            LpSolveResultParser parser = new LpSolveResultParser(variables);
            Throwable[] readerExceptionContainer = new Throwable[1];
            Thread readerThread = configureReaderThread(process.getInputStream(), parser, readerExceptionContainer);
            try {
                readerThread.start();

                // Write the model file to the lp_solve process (in the main thread)
                writeToProcess(lpSolveLines, process.getOutputStream());

                // Wait for the reader thread to terminate
                awaitReaderThread(readerThread, readerExceptionContainer);

                // Wait for the lp_solve process to terminate
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IllegalStateException("lp_solve terminated with non-zero exit code " + exitCode);
                }

                // We now have the calculated assignment
                Map<Integer, String> assignment = parser.getAssignment();
                log.debug("Calculated assignment: {}", assignment);
                return assignment;
            } finally {
                // Cleanup readerThread
                try {
                    if (readerThread.isAlive()) {
                        readerThread.interrupt();
                    }
                } catch (Throwable ignored) {
                    // keep original exception
                }
            }
        } finally {
            // Cleanup lp_solve process
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
                // keep original exception
            }
        }
    }

    @SuppressWarnings("DefaultCharset")
    private Thread configureReaderThread(InputStream inputStream, LpSolveResultParser parser, Throwable[] readerExceptionContainer) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    parser.parseLine(line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        readerThread.setUncaughtExceptionHandler((t, e) -> readerExceptionContainer[0] = e);
        return readerThread;
    }

    private void awaitReaderThread(Thread readerThread, Throwable[] readerExceptionContainer) throws InterruptedException {
        readerThread.join(); // also establishes memory synchronization from readerThread to main thread
        Throwable t = readerExceptionContainer[0];
        if (t != null) {
            throw new RuntimeException("Exception in reader thread", t);
        }
    }

    @SuppressWarnings("DefaultCharset")
    private void writeToProcess(Stream<String> lines, OutputStream outputStream) {
        try (PrintWriter writer = new PrintWriter(outputStream)) {
            if (log.isDebugEnabled()) {
                String content = lines.collect(Collectors.joining(System.lineSeparator()));
                log.debug("Generated lp_solve input:\n{}", content);
                writer.println(content);
            } else {
                lines.forEach(writer::println);
            }
        }
    }
}
