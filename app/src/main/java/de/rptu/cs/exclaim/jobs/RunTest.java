package de.rptu.cs.exclaim.jobs;

import com.fasterxml.jackson.databind.ObjectReader;
import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.schema.enums.BackgroundJobType;
import de.rptu.cs.exclaim.utils.JsonUtils;
import de.rptu.cs.exclaim.utils.RteServices;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;

import static de.rptu.cs.exclaim.jobs.PayloadHelpers.payloadToString;
import static de.rptu.cs.exclaim.jobs.PayloadHelpers.stringToPayload;

@Service
@Slf4j
public class RunTest implements JobService {
    private static final BackgroundJobType TYPE = BackgroundJobType.RUN_TEST;

    private final BackgroundJobExecutor backgroundJobExecutor;
    private final RteServices rteServices;
    private final ObjectReader objectReader;

    /**
     * Whether the RTE is enabled. If not, then all RTE jobs will be marked as failed permanently.
     */
    private final boolean enabled;

    @Getter(onMethod_ = {@Override})
    private final short maxParallel;

    public RunTest(ExclaimProperties exclaimProperties, BackgroundJobExecutor backgroundJobExecutor, RteServices rteServices, ObjectReader objectReader) {
        this.backgroundJobExecutor = backgroundJobExecutor;
        this.rteServices = rteServices;
        this.objectReader = objectReader;
        ExclaimProperties.Rte rte = exclaimProperties.getRte();
        this.enabled = rte.getEnabled();
        if (enabled) {
            maxParallel = rte.getMaxParallel();
            log.info("Allowing up to {} parallel RTE jobs", maxParallel);
        } else {
            maxParallel = Short.MAX_VALUE;
            log.info("RTE is disabled, will mark any RTE jobs as failed permanently.");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @SuppressWarnings("NullAway")
    private static class Key {
        String exerciseId;
        String sheetId;
        String assignmentId;
        String groupId;
        String teamId;
        int requestNr;
    }

    @Override
    public BackgroundJobType getType() {
        return TYPE;
    }

    @Override
    public void execute(@Nullable byte[] payload, JobContext context) throws IOException {
        if (!enabled) {
            throw new JobFailedPermanentlyException("RTE is disabled");
        }

        Key key = objectReader.readValue(payloadToString(payload), Key.class);
        log.debug("Executing job for {}", key);
        rteServices.runTest(key.exerciseId, key.sheetId, key.assignmentId, key.groupId, key.teamId, key.requestNr);
    }

    public void submit(String exerciseId, String sheetId, String assignmentId, String groupId, String teamId, int requestNr) {
        backgroundJobExecutor.submit(TYPE, stringToPayload(JsonUtils.toJson(
            new Key(exerciseId, sheetId, assignmentId, groupId, teamId, requestNr)
        )));
    }
}
