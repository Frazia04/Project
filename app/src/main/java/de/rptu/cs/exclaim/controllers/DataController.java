package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.data.GroupAndTeam;
import de.rptu.cs.exclaim.data.PreviewFileType;
import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.security.ExerciseRoles;
import de.rptu.cs.exclaim.utils.UploadManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.rptu.cs.exclaim.schema.tables.Studentresults.STUDENTRESULTS;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DataController {
    private final UploadManager uploadManager;
    private final MetricsService metrics;
    private final AccessChecker accessChecker;
    private final DSLContext ctx;

    @GetMapping(value = "/data/{exerciseId}/{sheetId}/{groupId}/{teamId}/{assignmentId}/{internalFilename:.*}")
    public void getFile(
        @PathVariable String exerciseId,
        @PathVariable String sheetId,
        @PathVariable String groupId,
        @PathVariable String teamId,
        @PathVariable String assignmentId,
        @PathVariable String internalFilename,
        HttpServletResponse response
    ) throws IOException {
        metrics.registerAccess();
        int userId = accessChecker.getUserId();
        ExerciseRoles exerciseRoles = accessChecker.getExerciseRoles(exerciseId);
        boolean canAssess = exerciseRoles.canAssess(groupId);
        if (!canAssess) {
            // Check if student can access that file
            GroupAndTeam groupAndTeam = exerciseRoles.getGroupAndTeam();
            if (groupAndTeam != null) {
                groupAndTeam = ctx
                    .select(STUDENTRESULTS.GROUPID, STUDENTRESULTS.TEAMID)
                    .from(STUDENTRESULTS)
                    .where(
                        STUDENTRESULTS.EXERCISE.eq(exerciseId),
                        STUDENTRESULTS.SHEET.eq(sheetId),
                        STUDENTRESULTS.USERID.eq(userId)
                    )
                    .fetchOptional(r -> new GroupAndTeam(r.value1(), r.value2()))
                    .orElse(groupAndTeam);
            }
            if (groupAndTeam == null || !groupId.equals(groupAndTeam.getGroupId()) || !teamId.equals(groupAndTeam.getTeamId())) {
                throw new AccessDeniedException("Cannot access that team");
            }
        }

        Path filePath = uploadManager.getUploadPath(exerciseId, sheetId, assignmentId, groupId, teamId, internalFilename);
        boolean isFeedback = assignmentId.equals(UploadManager.FEEDBACK_SUB);

        String headerFileName = isFeedback
            ? internalFilename
            : internalFilename.substring(15);

        headerFileName = headerFileName
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");

        String where;
        PreviewFileType fileType = PreviewFileType.byFilename(internalFilename);
        if (!isFeedback && fileType != PreviewFileType.NoPreview) {
            where = "inline";
            if (fileType == PreviewFileType.Text) {
                response.setContentType("text/plain; charset=utf-8");
            } else if (fileType == PreviewFileType.PDF) {
                response.setContentType(MediaType.APPLICATION_PDF.toString());
            }
        } else {
            where = "attachment";
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM.toString());
        }

        response.setHeader("Content-Disposition", where + "; filename=\"" + headerFileName + "\"");
        Files.copy(filePath, response.getOutputStream());
    }
}
