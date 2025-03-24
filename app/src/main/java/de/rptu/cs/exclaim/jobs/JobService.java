package de.rptu.cs.exclaim.jobs;

import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * A Job Service is responsible for executing background jobs of a specific type.
 */
public interface JobService {
    /**
     * Get the enum type this job service can handle.
     *
     * @return the enum type
     */
    BackgroundJobType getType();

    /**
     * Execute a job with the given payload.
     *
     * @param payload the payload for the job to execute
     * @param context the context of the job execution
     * @throws JobFailedPermanentlyException if the job execution failed permanently
     * @throws JobFailedTemporarilyException if the job execution failed temporarily
     * @throws Exception                     if the job execution failed temporarily
     */
    void execute(@Nullable byte[] payload, JobContext context) throws Exception;

    /**
     * How often should a job for this service be tried again if an error occurs?
     * The total number of attempts will be one plus the maximum retries.
     *
     * @return the maximum retry count
     */
    default short getMaxRetryCount() {
        return 9;
    }

    /**
     * How long to wait before the next retry attempt?
     *
     * @param retryCount number of the next retry attempt, always >= 1
     * @return how long to wait
     */
    default Duration getRetryDelay(short retryCount) {
        return switch (retryCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(30);
            case 4 -> Duration.ofHours(1);
            case 5 -> Duration.ofHours(3);
            case 6 -> Duration.ofHours(6);
            case 7 -> Duration.ofHours(12);
            default -> Duration.ofDays(1);
        };
    }

    /**
     * How many jobs does this service want to execute in parallel?
     * Default is Short.MAX_VALUE which means unlimited parallel execution.
     */
    default short getMaxParallel() {
        return Short.MAX_VALUE;
    }
}
