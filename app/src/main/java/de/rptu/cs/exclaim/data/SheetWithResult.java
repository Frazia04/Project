package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.ISheet;
import de.rptu.cs.exclaim.schema.enums.Attendance;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Immutable data object storing sheet data along with results for a single student, implementing the {@link ISheet}
 * interface.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SheetWithResult extends SheetWithMaxPoints {
    @Nullable BigDecimal points;
    @Nullable Attendance attended;
    boolean unreadAnnotations;

    public SheetWithResult(ISheet sheet, @Nullable BigDecimal maxPoints, @Nullable BigDecimal points, @Nullable Attendance attended, boolean unreadAnnotations) {
        super(sheet, maxPoints);
        this.points = points;
        this.attended = attended;
        this.unreadAnnotations = unreadAnnotations;
    }
}
