package de.rptu.cs.exclaim.jobs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * This object carries some contextual information for a specific job execution.
 * <p>
 * Using the {@link #setPayload(byte[])} method, the job payload can be updated in case of a temporary failure.
 */
@RequiredArgsConstructor
public class JobContext {
    /**
     * The retry counter for the current job execution (>= 0).
     */
    @Getter
    private final short retryCount;

    /**
     * Creation time of the current job (in UTC).
     */
    @Getter
    private final LocalDateTime created;

    /**
     * The updated payload, if it has been set.
     */
    @Nullable
    private volatile byte[] updatedPayload = null;

    /**
     * Update the payload of the current job execution.
     * <p>
     * Only relevant when the job terminates with a temporary failure - success or permanent failure deletes the job.
     *
     * @param payload the payload to set
     */
    public void setPayload(byte[] payload) {
        this.updatedPayload = Objects.requireNonNull(payload);
    }

    /**
     * Get the updated payload that has been set via {@link #setPayload(byte[])}.
     *
     * @return the updated payload, if it has been set, otherwise empty.
     */
    public Optional<byte[]> getUpdatedPayload() {
        return Optional.ofNullable(updatedPayload);
    }
}
