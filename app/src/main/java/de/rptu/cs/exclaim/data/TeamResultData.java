package de.rptu.cs.exclaim.data;

import lombok.Value;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Map;

@Value
public class TeamResultData {
    @Value
    public static class AssignmentResult {
        @Nullable BigDecimal points;
        int filesCount;
        @Nullable Integer testsPassed;
        @Nullable Integer testsTotal;
        @Nullable Integer testsRequestNr;
    }

    String groupId;
    String teamId;
    Map<String, AssignmentResult> assignmentResults;
    boolean hideComments;
    boolean hidePoints;
}
