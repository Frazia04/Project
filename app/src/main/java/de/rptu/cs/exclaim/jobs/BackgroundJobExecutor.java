package de.rptu.cs.exclaim.jobs;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.data.records.BackgroundJobRecord;
import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static de.rptu.cs.exclaim.schema.tables.BackgroundJobs.BACKGROUND_JOBS;

/**
 * This class is responsible for executing background jobs, represented as {@link BackgroundJobRecord} in the database.
 * <p>
 * The {@link #detectJobServices()} method is called during startup to detect all registered {@link JobService} beans.
 * When the application is ready, {@link #start()} forks a new thread that periodically checks for new jobs in the
 * database. The thread will be stopped gracefully when Spring {@link #destroy() destroys} this {@link DisposableBean}.
 * <p>
 * Background jobs that are due will be submitted to the {@link AsyncTaskExecutor} managed by Spring.
 * <p>
 * An external component can invoke {@link #pollNow()} when a new background job has been added to the database. It will
 * be picked up without further delay.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BackgroundJobExecutor implements DisposableBean {
    private final ExclaimProperties exclaimProperties;
    private final ApplicationContext applicationContext;
    private final DSLContext ctx;
    private final AsyncTaskExecutor applicationTaskExecutor;

    /**
     * Thread that periodically checks the database for new jobs.
     * Not volatile, because all accesses occur in synchronized methods.
     */
    @Nullable
    private Thread schedulerThread;

    /**
     * A lock to synchronize on for waiting in the {@link #schedulerThread}.
     */
    private final Object waitLock = new Object();

    /**
     * Whether the current waiting period should be aborted to immediately check for new jobs.
     * Not volatile, because all accesses occur in synchronized({@link #waitLock}) blocks.
     */
    private boolean skipNextWait = false;

    /**
     * Whether the application is shutting down and the {@link #schedulerThread} thus should terminate.
     * Not volatile, because all accesses are in synchronized methods {@link #start()} / {@link #destroy()} or we have a
     * memory barrier through synchronized({@link #waitLock}) between writing in {@link #destroy()} and reading in
     * {@link #schedulerLoop()}.
     */
    private boolean isShuttingDown = false;

    /**
     * The currently executing or queued background jobs. The set contains job ids.
     */
    private final Set<Long> executingBackgroundJobs = ConcurrentHashMap.newKeySet();

    /**
     * Mapping from the BackgroundJobType enum to the corresponding Job service instances.
     * Not synchronized, because it gets initialized once and then there is synchronization from {@link #start()} to the
     * synchronized(this) block in {@link #schedulerLoop()}.
     */
    private final Map<BackgroundJobType, JobServiceWithConcurrency> jobServices = new HashMap<>();

    @RequiredArgsConstructor
    private static class JobServiceWithConcurrency {
        /**
         * The job service
         */
        private final JobService jobService;

        /**
         * Concurrency restriction imposed on parallel jobs for this service. Can be null for unlimited parallel jobs.
         */
        @Nullable private final JobServiceConcurrency concurrency;
    }

    @RequiredArgsConstructor
    private static class JobServiceConcurrency {
        /**
         * Maximum number of parallel jobs
         */
        private final short maxParallel;

        /**
         * Number of currently executing jobs
         */
        private short currentWorkers = 0;

        /**
         * Queue of jobs that need to be executed (requires synchronization when accessed!)
         */
        private final Queue<BackgroundJobRecord> queue = new ArrayDeque<>();
    }

    // Called by Spring during application startup, after all beans have been registered, before executing runners.
    @EventListener(ApplicationStartedEvent.class)
    public void detectJobServices() {
        // Collect all registered job services
        for (JobService jobService : applicationContext.getBeansOfType(JobService.class).values()) {
            short maxParallel = jobService.getMaxParallel();
            if (maxParallel < 1) {
                throw new IllegalStateException(String.format(
                    "JobService %s has maxParallel of %s, must be at least 1.",
                    jobService, maxParallel
                ));
            }
            jobServices.compute(
                Objects.requireNonNull(jobService.getType(), "The job type must not be null!"),
                (jobType, duplicate) -> {
                    if (duplicate != null) {
                        throw new IllegalStateException(String.format(
                            "There are two different services registered for jobs of type %s: %s, %s",
                            jobType, duplicate.jobService, jobService
                        ));
                    }
                    return new JobServiceWithConcurrency(
                        jobService,
                        maxParallel == Short.MAX_VALUE ? null : new JobServiceConcurrency(maxParallel)
                    );
                }
            );
        }
        log.debug("Registered background job services: {}", jobServices);

        // Make sure that there is exactly one service for each job type
        if (jobServices.size() != BackgroundJobType.values().length) {
            Set<BackgroundJobType> missing = new HashSet<>(Arrays.asList(BackgroundJobType.values()));
            missing.removeAll(jobServices.keySet());
            throw new IllegalStateException("There is no service registered for the following background job types: " + missing);
        }
    }

    // Called by Spring when the application is ready, after executing runners.
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (isShuttingDown) {
            // destroy() was called before start()
            log.info("Application is shutting down, not starting the background job scheduler thread.");
        } else {
            log.info("Starting the background job scheduler thread...");
            schedulerThread = new Thread(this::schedulerLoop, "jobScheduler");
            schedulerThread.start();
        }
    }

    // Called by Spring on application shutdown
    @Override
    public synchronized void destroy() {
        if (isShuttingDown) return; // ignore repeated call of destroy()
        isShuttingDown = true;
        if (schedulerThread == null) {
            log.info("Background job scheduler thread has not yet been started, no need to stop it.");
        } else {
            log.info("Background job scheduler shutdown initiated...");

            // Notify the scheduler thread to abort/skip the waiting period.
            // That method synchronizes on waitLock and therefore also flushes isShuttingDown to the scheduler thread.
            notifyScheduler();

            try {
                schedulerThread.join(exclaimProperties.getBackgroundJobs().getShutdownTimeout().toMillis());
            } catch (InterruptedException e) {
                log.warn("Interruption while waiting for the scheduler thread to shutdown.", e);
                Thread.currentThread().interrupt();
            }
            if (schedulerThread.isAlive()) {
                log.error("Scheduler thread did not shutdown in time!");
            }
        }
    }

    /**
     * Notify the background job scheduler that a new background job might be available.
     * <p>
     * If the current thread has an active transaction, the notification gets delayed until the transaction is committed
     * such that the newly inserted job record is visible to the scheduler.
     */
    public void pollNow() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Active transaction detected, the background job scheduler will be notified after commit");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("Transaction completed, notifying the background job scheduler");
                    notifyScheduler();
                }
            });
        } else {
            log.debug("No active transaction detected, immediately notifying the background job scheduler");
            notifyScheduler();
        }
    }

    /**
     * Submit a new background job, i.e. save a record to the database that will be picked up by the job executor.
     *
     * @param jobType type of the background job
     * @param payload payload for the job
     */
    void submit(BackgroundJobType jobType, byte[] payload) {
        BackgroundJobRecord record = ctx.newRecord(BACKGROUND_JOBS);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        record.setCreated(now);
        record.setNextAttempt(now);
        record.setType(jobType);
        record.setPayload(payload);
        record.insert();
    }

    private void notifyScheduler() {
        synchronized (waitLock) {
            skipNextWait = true;
            waitLock.notify();
        }
    }

    /**
     * Loop to be executed in the {@link #schedulerThread}. Periodically calls {@link #checkForDueJobs()}.
     */
    private void schedulerLoop() {
        synchronized (this) {
            // Empty synchronized block to ensure that jobServices is up-to-date.
        }
        long interval = exclaimProperties.getBackgroundJobs().getPollInterval().toMillis();
        log.info("Background job scheduler started, will look for due jobs every {} ms.", interval);
        while (!isShuttingDown) {
            try {
                checkForDueJobs();
            } catch (Throwable e) {
                log.error("Unhandled exception in scheduler thread, will keep running.", e);
            }

            try {
                synchronized (waitLock) {
                    if (!skipNextWait) waitLock.wait(interval);
                    skipNextWait = false;
                }
            } catch (InterruptedException e) {
                if (isShuttingDown) break;
                else log.error("Unhandled interruption in scheduler thread, will keep running.", e);
            }
        }
        log.info("Background job scheduler shutdown completed.");
    }

    /**
     * Query the database for background jobs that are due and pass them to {@link #execute(BackgroundJobRecord, JobService, Runnable)}.
     */
    private void checkForDueJobs() {
        log.debug("Scheduler is checking for background jobs");
        List<BackgroundJobRecord> dueJobs = ctx.fetch(
            BACKGROUND_JOBS,
            BACKGROUND_JOBS.NEXT_ATTEMPT.le(LocalDateTime.now(ZoneOffset.UTC)),
            BACKGROUND_JOBS.ID.notIn(executingBackgroundJobs)
        );
        for (BackgroundJobRecord jobRecord : dueJobs) {
            BackgroundJobType jobType = jobRecord.getType();
            JobServiceWithConcurrency jobServiceWithConcurrency = jobServices.get(jobType);
            if (jobServiceWithConcurrency == null) {
                log.error("Cannot execute {} because there is no service registered for job type {}!", jobRecord, jobType);
                jobRecord.delete();
            } else {
                JobService jobService = jobServiceWithConcurrency.jobService;
                JobServiceConcurrency concurrency = jobServiceWithConcurrency.concurrency;
                if (executingBackgroundJobs.add(jobRecord.getBackgroundJobId())) {
                    if (concurrency == null) {
                        // No concurrency restrictions for this job type, we can execute the job directly.
                        execute(jobRecord, jobService, null);
                    } else {
                        // Concurrency for this job type is limited. We first queue the job. If the number of queue
                        // workers has not yet reached the maximum, then we start a queue worker that will eventually
                        // execute our queued job.

                        // We need synchronization to avoid the following race condition:
                        // - maxParallel is 1
                        // - A queue worker is running, holding the lock. The worker sees the queue empty.
                        // - Concurrently, a new job is queued, but no worker is started because of the lock.
                        // - The worker quits and releases the lock, leaving a job in the queue.

                        // noinspection SynchronizationOnLocalVariableOrMethodParameter
                        synchronized (concurrency) {
                            concurrency.queue.add(jobRecord);
                            if (concurrency.currentWorkers < concurrency.maxParallel) {
                                concurrency.currentWorkers++;
                                workQueue(jobService, concurrency);
                            } else {
                                log.info("Delaying job because service reached {} maximum parallel executions: {}", concurrency.maxParallel, jobRecord);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Work on a queue of jobs for a service with limited parallel executions.
     *
     * @param jobService  the service to execute the jobs with
     * @param concurrency the service's concurrency parameters
     */
    private void workQueue(JobService jobService, JobServiceConcurrency concurrency) {
        synchronized (concurrency) {
            BackgroundJobRecord jobRecord = concurrency.queue.poll();
            if (jobRecord != null) {
                execute(jobRecord, jobService, () -> workQueue(jobService, concurrency));
            } else {
                // No more queued jobs, the worker is done. Release its lock.
                concurrency.currentWorkers--;
            }
        }
    }

    /**
     * Execute the background job using the provided service.
     *
     * @param jobRecord  the record of the job to execute
     * @param jobService the service to execute the job with
     * @param callback   a callback to be run when the job completed (successfully or failed)
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void execute(BackgroundJobRecord jobRecord, JobService jobService, @Nullable Runnable callback) {
        log.debug("Submitting task to execute {} with {}", jobRecord, jobService);
        long jobId = jobRecord.getBackgroundJobId();
        JobContext context = new JobContext(jobRecord.getRetryCount(), jobRecord.getCreated());
        try {
            applicationTaskExecutor.<Void>submitCompletable(() -> {
                log.debug("Executing {}", jobRecord);
                jobService.execute(jobRecord.getPayload(), context);
                return null; // such that the lambda is a callable instead of a runnable (required for throwing Exception)
            }).whenComplete((ignored, ex) -> {
                try {
                    if (ex == null) {
                        log.debug("Job completed successfully: {}", jobRecord);
                        jobRecord.delete();
                    } else {
                        if (ex instanceof JobFailedPermanentlyException) {
                            log.error("Job failed permanently: {}", jobRecord, ex);
                            jobRecord.delete();
                        } else {
                            short retryCount = jobRecord.getRetryCount();
                            if (retryCount < jobService.getMaxRetryCount()) {
                                retryCount++;
                                Duration delay = jobService.getRetryDelay(retryCount);
                                jobRecord.setRetryCount(retryCount);
                                jobRecord.setNextAttempt(LocalDateTime.now(ZoneOffset.UTC).plus(delay));
                                context.getUpdatedPayload().ifPresent(jobRecord::setPayload);
                                log.warn("Job failed, will be retried: {}", jobRecord, ex);
                                jobRecord.update();
                            } else {
                                log.error("Job failed and exceeded max retry count: {}", jobRecord, ex);
                                jobRecord.delete();
                            }
                        }
                    }
                } finally {
                    executingBackgroundJobs.remove(jobId);
                    if (callback != null) {
                        callback.run();
                    }
                }
            });
        } catch (TaskRejectedException e) {
            log.error("Executor rejected task to execute background job, will try again on next polling: {}", jobRecord, e);
            executingBackgroundJobs.remove(jobId);
            if (callback != null) {
                callback.run();
            }
        }
    }
}
