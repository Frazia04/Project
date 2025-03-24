package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IStudent;
import de.rptu.cs.exclaim.data.interfaces.IUser;
import de.rptu.cs.exclaim.schema.enums.Attendance;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.io.Serializable;

@Value
public class StudentWithAttendance implements Serializable {
    IStudent student;
    IUser user;
    @Nullable Attendance attendance;
}
