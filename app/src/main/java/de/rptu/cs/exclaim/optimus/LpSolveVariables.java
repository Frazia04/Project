package de.rptu.cs.exclaim.optimus;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Manage lp_solve variables
 * <p>
 * This class manages the relation between variable names in our lp_solve model and groupIds from
 * our application. While we can use numeric userIds as is, the String groupIds need to be mapped
 * to numbers to safely build variable names in lp_solve.
 */
class LpSolveVariables {
    /**
     * userIds that are known to our model
     */
    @Getter(AccessLevel.PACKAGE)
    private final Collection<Integer> userIds;

    private final Map<String, Integer> groupStringToInt;
    private final String[] groupIntToString;

    LpSolveVariables(Collection<Integer> userIds, Collection<String> groupIds) {
        this.userIds = userIds;

        // Create a numeric id for each group
        int numberOfGroups = groupIds.size();
        groupStringToInt = new HashMap<>(numberOfGroups);
        groupIntToString = new String[numberOfGroups];
        int i = 0;
        for (String groupId : groupIds) {
            groupStringToInt.put(groupId, i);
            groupIntToString[i] = groupId;
            i++;
        }
    }

    String stringGroupId(int numericGroupId) {
        return groupIntToString[numericGroupId];
    }

    int numericGroupId(String stringGroupId) {
        return Objects.requireNonNull(groupStringToInt.get(stringGroupId), "unknown groupId");
    }

    IntStream streamNumericGroupIds() {
        return IntStream.range(0, groupIntToString.length);
    }
}
