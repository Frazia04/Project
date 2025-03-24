package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IExercise;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Immutable data object storing exercise data along with a registered flag, implementing the {@link IExercise} interface.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExerciseWithRegistered extends Exercise {
    boolean registered;

    public ExerciseWithRegistered(IExercise exercise, boolean registered) {
        super(exercise);
        this.registered = registered;
    }
}
