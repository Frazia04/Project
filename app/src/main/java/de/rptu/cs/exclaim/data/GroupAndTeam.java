package de.rptu.cs.exclaim.data;

import de.rptu.cs.exclaim.data.interfaces.IStudent;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.lang.Nullable;

import java.io.Serializable;

@Value
@NonFinal
public class GroupAndTeam implements Serializable {
    @Nullable String groupId;
    @Nullable String teamId;

    public GroupAndTeam(@Nullable String groupId, @Nullable String teamId) {
        if (groupId == null && teamId != null) {
            throw new IllegalArgumentException("teamId requires groupId");
        }
        this.groupId = groupId;
        this.teamId = teamId;
    }

    public GroupAndTeam(IStudent student) {
        this(student.getGroupId(), student.getTeamId());
    }
}
