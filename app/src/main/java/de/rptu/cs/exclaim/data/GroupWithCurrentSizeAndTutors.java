package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IGroup;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.List;

/**
 * Immutable data object storing group data along with the current size and tutors, implementing the {@link IGroup}
 * interface.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GroupWithCurrentSizeAndTutors extends Group {
    int currentSize;
    List<? extends IUser> tutors;

    public GroupWithCurrentSizeAndTutors(IGroup group, int currentSize, List<? extends IUser> tutors) {
        super(group);
        this.currentSize = currentSize;
        this.tutors = tutors;
    }
}
