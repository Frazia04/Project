package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IExam;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Immutable data object storing exam data along with a registered flag, implementing the {@link IExam} interface.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamWithRegistered extends Exam {
    boolean registered;

    public ExamWithRegistered(IExam exam, boolean registered) {
        super(exam);
        this.registered = registered;
    }
}
