package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IStudent;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.schema.enums.Attendance;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Data object storing user data and the current group/team of a student for a single exercise along with results for
 * all exercise sheets.
 */
@Value
public class StudentWithSheetResults implements Serializable {
    @Value
    public static class SheetResult {
        @Nullable String groupId;
        @Nullable String teamId;
        @Nullable BigDecimal teampoints;
        @Nullable BigDecimal deltapoints;
        boolean hidePoints;
        @Nullable Attendance attended;

        @Nullable
        public BigDecimal getPoints() {
            return hidePoints ? null
                : teampoints == null ? deltapoints
                : deltapoints == null ? teampoints
                : teampoints.add(deltapoints);
        }
    }

    IStudent student;
    IUser user;
    Map<String, SheetResult> sheetResults;

    public StudentWithSheetResults(IStudent student, IUser user, Map<String, SheetResult> sheetResults) {
        this.student = student;
        this.user = user;
        this.sheetResults = Collections.unmodifiableMap(sheetResults);
    }
}
