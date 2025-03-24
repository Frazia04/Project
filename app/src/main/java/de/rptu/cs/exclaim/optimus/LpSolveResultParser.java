package de.rptu.cs.exclaim.optimus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that parses the output of lp_solve.
 */
@Slf4j
class LpSolveResultParser {
    // the variable name needs to match LpSolveFileGenerator.userInGroupVariableName
    private static final Pattern PATTERN = Pattern.compile("u(?<user>\\d+)g(?<group>\\d+)\\s+(?<result>[01])");
    private final LpSolveVariables variables;

    /**
     * The parsed assignment (userId -> groupId)
     */
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, String> assignment;

    LpSolveResultParser(LpSolveVariables variables) {
        this.variables = variables;
        this.assignment = new HashMap<>(variables.getUserIds().size());
    }

    /**
     * Parse a single output line of lp_solve
     *
     * @param line the line to parse
     */
    void parseLine(String line) {
        Matcher matcher = PATTERN.matcher(line);
        if (matcher.matches()) {
            if (matcher.group("result").equals("1")) {
                int userId = Integer.parseInt(matcher.group("user"));
                int numericGroupId = Integer.parseInt(matcher.group("group"));
                String groupId = variables.stringGroupId(numericGroupId);
                log.debug("Assigning user {} to group {}", userId, groupId);
                assignment.compute(userId, (ignored, duplicate) -> {
                    if (duplicate != null) {
                        throw new IllegalStateException("Optimus would like to put user " + userId + " in group " + duplicate + " and " + groupId);
                    }
                    return groupId;
                });
            }
        } else {
            log.debug("Ignoring line: {}", line);
        }
    }
}
