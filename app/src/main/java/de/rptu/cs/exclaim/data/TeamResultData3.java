package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.ITestResult;
import de.rptu.cs.exclaim.utils.Markdown;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
public class TeamResultData3 {
    @Value
    public static class TeamMember {
        int userId;
        String firstname;
        String lastname;
        @Nullable String studentId;
        @Nullable BigDecimal deltapoints;
        @Nullable String deltapointsReason;
    }

    @Value
    public static class AssignmentResult {
        @Nullable BigDecimal points;
        @Nullable String comment;
        List<Upload> currentFiles;
        List<Upload> deletedFiles;
        @Nullable LocalDateTime snapshot;
        @Nullable ITestResult testResult;

        @Nullable
        public String getCommentHtml() {
            return comment == null ? null : Markdown.toHtml(comment);
        }
    }

    @Value
    public static class Upload {
        String filename;
        LocalDateTime uploadDate;
        @Nullable LocalDateTime deleteDate;
        @Nullable TeamMember uploader;
        @Nullable TeamMember deleter;
    }

    String groupId;
    String teamId;
    List<TeamMember> teamMembers;
    Map<String, AssignmentResult> assignmentResults;
    @Nullable String comment;
    boolean hideComments;
    boolean hidePoints;

    @Nullable
    public String getCommentHtml() {
        return comment == null ? null : Markdown.toHtml(comment);
    }
}
