package de.rptu.cs.exclaim.jobs;

import lombok.NoArgsConstructor;

/**
 * Exception that can be thrown by a {@link JobService} when execution of the job failed and should be retried. The
 * retry is attempted after some delay (as specified by {@link JobService#getRetryDelay(short)}) and the maximum number
 * of attempts is limited (see {@link JobService#getMaxRetryCount()}).
 * <p>
 * Any other Exception (except for {@link JobFailedPermanentlyException}) can be used as well. This exception is useful
 * for wrapping the actual cause and adding a job-specific message.
 * <p>
 * This is a {@link RuntimeException} such that it can be thrown inside lambda expressions for e.g. {@link Runnable} and
 * {@link java.util.function.Consumer}.
 */
@NoArgsConstructor
public class JobFailedTemporarilyException extends RuntimeException {
    public JobFailedTemporarilyException(String message) {
        super(message);
    }

    public JobFailedTemporarilyException(String message, Throwable cause) {
        super(message, cause);
    }

    public JobFailedTemporarilyException(Throwable cause) {
        super(cause);
    }
}
