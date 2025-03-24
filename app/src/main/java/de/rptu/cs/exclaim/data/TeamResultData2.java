package de.rptu.cs.exclaim.data;

import lombok.Value;
import org.springframework.lang.Nullable;

import java.util.Map;

@Value
public class TeamResultData2 {
    Map<String, TeamResultData.AssignmentResult> assignmentResults;
    boolean hideComments;
    boolean hidePoints;
    @Nullable String comment;
}
