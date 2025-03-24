package de.rptu.cs.exclaim.jobs;

import lombok.NoArgsConstructor;

/**
 * Exception to be thrown by a {@link JobService} when execution of the job failed and should not be retried.
 * <p>
 * This is a {@link RuntimeException} such that it can be thrown inside lambda expressions for e.g. {@link Runnable} and
 * {@link java.util.function.Consumer}.
 */
@NoArgsConstructor
public class JobFailedPermanentlyException extends RuntimeException {
    public JobFailedPermanentlyException(String message) {
        super(message);
    }

    public JobFailedPermanentlyException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobFailedPermanentlyException(Throwable cause) {
        super(cause);
    }
}
