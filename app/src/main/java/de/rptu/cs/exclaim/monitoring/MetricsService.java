package de.rptu.cs.exclaim.monitoring;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MetricsService {
    @Getter private final CollectorRegistry registry;
    private final Counter pageAccessCounter;
    private final Counter exerciseAccessCounter;
    private final Histogram uploadSize;
    private final Counter errorCounter;

    public MetricsService(CollectorRegistry registry) {
        this.registry = registry;

        pageAccessCounter = Counter.build()
            .name("exclaim_access_total")
            .help("Total number of accesses")
            .labelNames("page")
            .register(registry);
        exerciseAccessCounter = Counter.build()
            .name("exclaim_exercise_access")
            .help("Accesses per exercise")
            .labelNames("exercise")
            .register(registry);
        uploadSize = Histogram.build()
            .name("exclaim_upload_size")
            .help("Size of uploaded files")
            .buckets(1024, 102400, 1048567) // 1KB 100KB 1MB
            .register(registry);
        errorCounter = Counter.build()
            .name("exclaim_errors_total")
            .help("Total number of errors")
            .register(registry);
    }

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile(".*\\.(.*)");

    private void lookupAndRegisterNameOfCaller() {
        StackWalker.getInstance().walk(
            // Skip 2 stack frames:
            // 1. this private method
            // 2. caller from this class
            s -> s.skip(2).findFirst()
        ).ifPresentOrElse(
            stackFrame -> {
                // Extract the class name (basename only, without package) and method name
                String className = stackFrame.getClassName();
                Matcher matcher = CLASS_NAME_PATTERN.matcher(className);
                if (matcher.matches()) {
                    className = matcher.group(1);
                }
                String key = className + "::" + stackFrame.getMethodName();
                pageAccessCounter.labels(key).inc();
            }, () -> log.error("Could not detect caller of MetricsService.", new Exception(""))
        );
    }

    public void registerAccess() {
        lookupAndRegisterNameOfCaller();
    }

    public void registerAccessExercise(String exercise) {
        lookupAndRegisterNameOfCaller();
        exerciseAccessCounter.labels(exercise).inc();
    }

    public void registerUploadSize(double size) {
        uploadSize.observe(size);
    }

    public void registerException() {
        errorCounter.inc();
    }
}
