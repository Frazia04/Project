package de.rptu.cs.exclaim.data;

public class NonNullGroupAndTeam extends GroupAndTeam {
    public NonNullGroupAndTeam(String groupId, String teamId) {
        super(groupId, teamId);
    }

    @Override
    @SuppressWarnings("NullAway")
    public String getGroupId() {
        return super.getGroupId();
    }

    @Override
    @SuppressWarnings("NullAway")
    public String getTeamId() {
        return super.getTeamId();
    }
}
