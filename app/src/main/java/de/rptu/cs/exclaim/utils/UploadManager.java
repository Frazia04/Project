package de.rptu.cs.exclaim.utils;

import de.rptu.cs.exclaim.data.interfaces.IUpload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UploadManager {
    public static final DateTimeFormatter INTERNAL_DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final String DATA_PATH = "data/";
    public static final String FEEDBACK_SUB = "__feedback";
    public static final char PATH_SEPARATOR = System.getProperty("os.name").toUpperCase(Locale.ROOT).contains("WINDOWS") ? '-' : '|';

    public Path getUploadPath(String exerciseId, String sheetId, String assignmentId, String groupId, String teamId, String internalFilename) {
        return teamUploadFolder(exerciseId, sheetId, assignmentId, groupId, teamId).resolve(internalFilename);
    }

    public Path getUploadPath(String exerciseId, String sheetId, String assignmentId, String groupId, String teamId, LocalDateTime uploadDate, String filename) {
        return getUploadPath(exerciseId, sheetId, assignmentId, groupId, teamId, INTERNAL_DTF.format(uploadDate) + "-" + filename);
    }

    public Path getUploadPath(IUpload upload) {
        return getUploadPath(
            upload.getExerciseId(),
            upload.getSheetId(),
            upload.getAssignmentId(),
            upload.getGroupId(),
            upload.getTeamId(),
            upload.getUploadDate(),
            upload.getFilename()
        );
    }

    public List<String> getFeedbackUploads(String exerciseId, String sheetId, String groupId, String teamId) throws IOException {
        Path feedbackUploadPath = getFeedbackUploadFolder(exerciseId, sheetId, groupId, teamId);
        if (Files.isDirectory(feedbackUploadPath)) {
            try (Stream<Path> files = Files.walk(feedbackUploadPath, 1)) {
                return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    public Path getFeedbackUploadFolder(String exerciseId, String sheetId, String groupId, String teamId) {
        return teamUploadFolder(exerciseId, sheetId, FEEDBACK_SUB, groupId, teamId);
    }

    private Path teamUploadFolder(String exerciseId, String sheetId, String assignmentId, String groupId, String teamId) {
        return Path.of(DATA_PATH, exerciseId, sheetId, groupId + PATH_SEPARATOR + teamId, assignmentId);
    }
}
