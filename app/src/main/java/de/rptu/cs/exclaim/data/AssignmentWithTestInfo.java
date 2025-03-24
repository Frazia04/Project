package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IAssignment;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AssignmentWithTestInfo extends Assignment {
    boolean testExists;

    public AssignmentWithTestInfo(IAssignment assignment, boolean testExists) {
        super(assignment);
        this.testExists = testExists;
    }
}
