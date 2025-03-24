package de.rptu.cs.exclaim.data;

import lombok.Value;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

@Value
public class ExamTaskWithPoints {
    String taskId;
    BigDecimal maxPoints;
    @Nullable BigDecimal points;
}
