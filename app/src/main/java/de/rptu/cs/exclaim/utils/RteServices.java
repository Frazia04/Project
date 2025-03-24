package de.rptu.cs.exclaim.utils;

import de.rptu.cs.exclaim.ExclaimProperties;
import de.rptu.cs.exclaim.data.FileWarnings;
import de.rptu.cs.exclaim.data.RteListTestsResponse;
import de.rptu.cs.exclaim.data.RteResult;
import de.rptu.cs.exclaim.data.TestResultDetails;
import de.rptu.cs.exclaim.data.records.TestResultRecord;
import de.rptu.cs.exclaim.data.records.UploadRecord;
import de.rptu.cs.exclaim.data.records.WarningRecord;
import de.rptu.cs.exclaim.jobs.JobFailedPermanentlyException;
import de.rptu.cs.exclaim.jobs.JobFailedTemporarilyException;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static de.rptu.cs.exclaim.schema.tables.Testresult.TESTRESULT;
import static de.rptu.cs.exclaim.schema.tables.Uploads.UPLOADS;
import static de.rptu.cs.exclaim.schema.tables.Warnings.WARNINGS;

@Service
@Slf4j
@RequiredArgsConstructor
public class RteServices {
    private final ExclaimProperties exclaimProperties;
    private final UploadManager uploadManager;
    private final SimpMessagingTemplate broker;
    private final DSLContext ctx;
    @Nullable private volatile TestCache availableTestsCache;

    @Value
    public static class TestName {
        String exerciseId;
        String sheetId;
        String assignmentId;
    }

    @Value
    public static class TestResultMsg {
        String exercise;
        String sheet;
        String assignment;
        String group;
        String team;
        int request;
        String status;
    }

    @Value
    private static class TestCache {
        LocalDateTime time;
        Set<TestName> tests;
    }


    public void runTest(String exerciseId, String sheetId, String assignmentId, String groupId, String teamId, int requestNr) {
        ExclaimProperties.Rte rte = exclaimProperties.getRte();
        String rteUrl = rte.getUrl();
        String apiKey = rte.getApiKey();
        if (StringUtils.isEmpty(rteUrl) || StringUtils.isEmpty(apiKey)) {
            throw new JobFailedPermanentlyException("Invalid RTE configuration");
        }

        TestResultRecord record = ctx
            .fetchOptional(TESTRESULT,
                TESTRESULT.EXERCISE.eq(exerciseId),
                TESTRESULT.SHEET.eq(sheetId),
                TESTRESULT.ASSIGNMENT.eq(assignmentId),
                TESTRESULT.GROUPID.eq(groupId),
                TESTRESULT.TEAMID.eq(teamId),
                TESTRESULT.REQUESTNR.eq(requestNr)
            )
            .orElseThrow(() -> new JobFailedPermanentlyException("Database record not found"));
        log.debug("Processing RTE job for {}", record);
        if (record.getTimeDone() != null) {
            log.warn("Test is marked as done, ignoring it: {}", record);
        } else {
            LocalDateTime snapshot = record.getSnapshot();
            List<UploadRecord> uploads = ctx.fetch(
                UPLOADS,
                UPLOADS.EXERCISE.eq(exerciseId),
                UPLOADS.SHEET.eq(sheetId),
                UPLOADS.ASSIGNMENT.eq(assignmentId),
                UPLOADS.GROUPID.eq(groupId),
                UPLOADS.TEAMID.eq(teamId),
                UPLOADS.UPLOAD_DATE.le(snapshot),
                UPLOADS.DELETE_DATE.isNull().or(UPLOADS.DELETE_DATE.gt(snapshot))
            );
            LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
            params.add("test", exerciseId + "/" + sheetId + "/" + assignmentId);
            params.add("numfiles", uploads.size());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            int i = 0;
            for (UploadRecord upload : uploads) {
                File file = uploadManager.getUploadPath(upload).toFile();
                if (!file.isFile()) {
                    throw new JobFailedPermanentlyException("File not found: " + file.getAbsolutePath());
                }
                params.add("file" + i++, new FileSystemResource(file) {
                    @Override
                    public String getFilename() {
                        return upload.getFilename();
                    }
                });
            }

            // If it had been started previously, then this is a retry
            if (record.getTimeStarted() != null) {
                record.setRetries(record.getRetries() + 1);
            }


            record.setTimeStarted(LocalDateTime.now(exclaimProperties.getTimezone()));
            ResponseEntity<String> response = new RestTemplate().postForEntity(
                UriComponentsBuilder.fromHttpUrl(rteUrl + "/test").queryParam("apiKey", apiKey).build().toUri(),
                new HttpEntity<>(params, headers),
                String.class
            );
            String body;
            if (response.getStatusCode().is2xxSuccessful() && (body = response.getBody()) != null) {
                RteResult result = RteResult.fromJson(body);
                record.setTimeDone(LocalDateTime.now(exclaimProperties.getTimezone()));
                TestResultDetails details = result.getTest_result();
                record.setCompiled(details.isCompiled());
                record.setInternalError(details.getInternal_error() != null);
                record.setTestsTotal(details.getTests_executed());
                record.setTestsPassed(details.getTests_executed() - details.getTests_failed());
                record.setMissingFiles(details.hasMissing_files());
                record.setIllegalFiles(details.hasIllegal_files());
                record.setResult(JsonUtils.toJson(details));
                record.update();

                ctx
                    .deleteFrom(WARNINGS)
                    .where(WARNINGS.FILEID.in(uploads.stream().map(UploadRecord::getUploadId).toList()))
                    .execute();
                List<FileWarnings> fileWarnings = result.getFile_warnings();
                if (fileWarnings != null && !fileWarnings.isEmpty()) {
                    Map<String, Integer> uploadIdByFilename = uploads.stream().collect(Collectors.toMap(UploadRecord::getFilename, UploadRecord::getUploadId));
                    List<WarningRecord> warningRecords = new ArrayList<>();
                    for (FileWarnings file : fileWarnings) {
                        String filename = file.getFilename();
                        Integer fileId = uploadIdByFilename.get(filename);
                        if (fileId == null) {
                            log.warn("Could not find upload for file {}", filename);
                        } else {
                            for (FileWarnings.Warning warning : file.getWarnings()) {
                                WarningRecord warningRecord = ctx.newRecord(WARNINGS);
                                warningRecord.setFileId(fileId);
                                warningRecord.setLine(warning.getBegin_line());
                                warningRecord.setRule(warning.getRule());
                                warningRecord.setRuleset(warning.getRule_set());
                                warningRecord.setInfoUrl(warning.getInfo_url());
                                warningRecord.setPriority(warning.getPriority());
                                warningRecord.setMessage(warning.getMessage());
                                warningRecords.add(warningRecord);
                            }
                        }
                    }
                    ctx.batchUpdate(warningRecords).execute();
                }
            } else {
                broker.convertAndSend(
                    testResultsChannel(exerciseId, sheetId, groupId, teamId),
                    new TestResultMsg(exerciseId, sheetId, assignmentId, groupId, teamId, requestNr, "failed")
                );

                throw new JobFailedTemporarilyException("Communication problem with RTE");
            }
        }

        broker.convertAndSend(
            testResultsChannel(exerciseId, sheetId, groupId, teamId),
            new TestResultMsg(exerciseId, sheetId, assignmentId, groupId, teamId, requestNr, "done")
        );
    }


    public boolean isTestAvailable(String exerciseId, String sheetId, String assignmentId) {
        return availableTests().contains(new TestName(exerciseId, sheetId, assignmentId));
    }

    public static String testResultsChannel(String exerciseId, String sheetId, String groupId, String teamId) {
        return "/topic/testresults/" + exerciseId + "/" + sheetId + "/" + groupId + "/" + teamId;
    }

    private Set<TestName> availableTests() {
        TestCache cached = availableTestsCache;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (cached == null || cached.time.isBefore(now.minus(20, ChronoUnit.SECONDS))) {
            cached = new TestCache(now, loadAvailableTests());
            availableTestsCache = cached;
        }
        return cached.tests;
    }

    @SuppressWarnings("StringSplitter")
    private Set<TestName> loadAvailableTests() {
        ExclaimProperties.Rte rte = exclaimProperties.getRte();
        if (!rte.getEnabled()) {
            return Collections.emptySet();
        }
        String rteUrl = rte.getUrl();
        String apiKey = rte.getApiKey();
        if (StringUtils.isEmpty(rteUrl) || StringUtils.isEmpty(apiKey)) {
            throw new IllegalStateException("Invalid RTE configuration");
        }

        ResponseEntity<String> response = new RestTemplate().getForEntity(
            UriComponentsBuilder.fromHttpUrl(rteUrl + "/listtests").queryParam("apiKey", apiKey).build().toUri(),
            String.class
        );
        String body;
        try {
            if (response.getStatusCode().is2xxSuccessful() && (body = response.getBody()) != null) {
                return RteListTestsResponse.fromJson(body).getTests().stream()
                    .map(path -> {
                        String[] parts = path.split("[/\\\\]");
                        return parts.length == 3
                            ? new TestName(parts[0], parts[1], parts[2])
                            : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            } else {
                log.error("Could not update available tests from RTE");
            }
        } catch (Exception e) {
            log.error("Could not update available tests from RTE", e);
        }
        return Collections.emptySet();
    }
}
