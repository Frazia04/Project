package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.ISheet;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Immutable data object storing sheet data along with the maximum achievable points, implementing the {@link ISheet}
 * interface.
 */
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SheetWithMaxPoints extends Sheet {
    BigDecimal maxPoints;

    public SheetWithMaxPoints(ISheet sheet, @Nullable BigDecimal maxPoints) {
        super(sheet);
        this.maxPoints = maxPoints != null ? maxPoints : BigDecimal.ZERO;
    }
}
