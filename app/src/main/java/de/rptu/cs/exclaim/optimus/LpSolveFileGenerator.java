package de.rptu.cs.exclaim.optimus;

import de.rptu.cs.exclaim.schema.enums.GroupPreferenceOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class to generate a lp_solve model in the
 * <a href="https://lpsolve.sourceforge.net/5.5/lp-format.htm">LP file format</a>.
 */
@Slf4j
@RequiredArgsConstructor
class LpSolveFileGenerator {
    private static final boolean enableComments = log.isDebugEnabled();
    private final List<String> groupIds;
    private final Map<Integer, Map<String, GroupPreferenceOption>> groupPreferences;
    private final Map<Integer, List<Integer>> teamPreferences;
    private final LpSolveVariables variables;

    /**
     * Generates the model file
     *
     * @return stream of lines
     */
    Stream<String> generateLines() {
        return Stream.of(
            objectiveFunction(),
            constraints(),
            declarations()
        ).flatMap(Function.identity());
    }


    // -----------------------------------------------------------------------------------
    // Objective Function

    private Stream<String> objectiveFunction() {
        return addCommentAndEmptyLine(
            "// objective function to minimize",
            Stream.of(
                Stream.of("min:"),
                weightsGroupPreferences(),
                weightsBrokenFriendships(),
                Stream.of("  ;")
            ).flatMap(Function.identity())
        );
    }

    private Stream<String> weightsGroupPreferences() {
        // Add a weight depending on the group each user is assigned to. Higher weight is worse assignment.
        return addCommentAndEmptyLine(
            "  // group preferences",
            groupPreferences.entrySet().stream().map(entry -> {
                int userId = entry.getKey();
                Map<String, GroupPreferenceOption> gp = entry.getValue();
                return " " + groupIds.stream().map(stringGroupId -> {
                        GroupPreferenceOption preference = gp.get(stringGroupId);
                        int weight = preference == null ? 50 : switch (preference) {
                            case PREFERRED -> 1;
                            case POSSIBLE -> 3;
                            case DISLIKE -> 10;
                            case IMPOSSIBLE -> 200;
                        };
                        return " + " + weight + " * " + userInGroupVariableName(userId, variables.numericGroupId(stringGroupId));
                    })
                    .collect(Collectors.joining());
            })
        );
    }

    private Stream<String> weightsBrokenFriendships() {
        // Add a weight for each broken friendship.
        return addComment(
            "  // team preferences",
            teamPreferences.entrySet().stream().flatMap(entry -> {
                int userId = entry.getKey();
                List<Integer> friendUserIds = entry.getValue();
                if (friendUserIds.isEmpty()) {
                    return Stream.empty();
                }

                // The more friends a user has, the less is the penalty for breaking a friendship.
                int weight = 100 / friendUserIds.size();
                return Stream.of(
                    " " + friendUserIds.stream().map(friendUserId ->
                        " + " + weight + " * " + brokenFriendshipVariableName(userId, friendUserId)
                    ).collect(Collectors.joining())
                );
            })
        );
    }


    // -----------------------------------------------------------------------------------
    // Constraints

    private Stream<String> constraints() {
        return Stream.of(
            constraintUserInExactlyOneGroup(),
            constraintGroupSizes(),
            constraintBrokenFriendships()
        ).flatMap(Function.identity());
    }

    private Stream<String> constraintUserInExactlyOneGroup() {
        return addCommentAndEmptyLine(
            "// select exactly one group for each user",
            variables.getUserIds().stream().map(userId ->
                variables.streamNumericGroupIds().mapToObj(numericGroupId ->
                    userInGroupVariableName(userId, numericGroupId)
                ).collect(Collectors.joining(" + ", "", " = 1;"))
            )
        );
    }

    private Stream<String> constraintGroupSizes() {
        int minSize = Math.max(0, groupPreferences.size() / groupIds.size() - 1);
        int maxSize = minSize + 2;
        return addCommentAndEmptyLine(
            "// enforce group size limits",
            variables.streamNumericGroupIds().mapToObj(numericGroupId -> {
                // break up long line in smaller chunks of 10 entries
                Stream.Builder<String> lines = Stream.builder();
                StringBuilder line = new StringBuilder(128).append(minSize).append(" <= ");
                byte i = 0;
                boolean first = true;
                for (int userId : variables.getUserIds()) {
                    if (i == 10) {
                        lines.add(line.toString());
                        line = new StringBuilder(128).append(" ");
                        i = 0;
                    }
                    if (first) {
                        first = false;
                    } else {
                        line.append(" + ");
                    }
                    line.append(userInGroupVariableName(userId, numericGroupId));
                    i++;
                }
                return lines.add(line.append(" <= ").append(maxSize).append(";").toString()).build();
            }).flatMap(Function.identity())
        );
    }

    private Stream<String> constraintBrokenFriendships() {
        return addCommentAndEmptyLine(
            "// check for broken friendships (unsatisfied team preferences)",
            teamPreferences.entrySet().stream().flatMap(entry -> {
                int userId = entry.getKey();
                return entry.getValue().stream().flatMap(friendUserId ->
                    variables.streamNumericGroupIds().mapToObj(numericGroupId ->
                        // The friendship is broken if the users are in different groups.
                        // In that case, there are groups X != Y such that:
                        //   userInGroupVariableName(userId, X) -> 1
                        //   userInGroupVariableName(friendUserId, X) -> 0
                        //   userInGroupVariableName(userId, Y) -> 0
                        //   userInGroupVariableName(friendUserId, Y) -> 1
                        // We only need to look for the X group, because friendUserId being in a group Y without userId
                        // will always result in userId being in another group without friendUserId. Check for different
                        // binary values by subtraction. The inverse doesn't harm because "b >= -1" is always satisfied.
                        brokenFriendshipVariableName(userId, friendUserId) + " >= " +
                            userInGroupVariableName(userId, numericGroupId) + " - " +
                            userInGroupVariableName(friendUserId, numericGroupId) + ";"
                    )
                );
            })
        );
    }


    // -----------------------------------------------------------------------------------
    // Declarations

    private Stream<String> declarations() {
        return Stream.concat(
            userInGroupVariableDeclarations(),
            brokenFriendshipVariableDeclarations()
        );
    }

    private Stream<String> userInGroupVariableDeclarations() {
        return addCommentAndEmptyLine(
            "// variables denoting whether a user is in a specific group",
            variables.getUserIds().stream().map(userId ->
                variables.streamNumericGroupIds()
                    .mapToObj(numericGroupId -> userInGroupVariableName(userId, numericGroupId))
                    .collect(Collectors.joining(", ", "bin ", ";"))
            )
        );
    }

    private Stream<String> brokenFriendshipVariableDeclarations() {
        return addComment(
            "// variables denoting whether a friendship has been broken (unsatisfied team preference)",
            teamPreferences.entrySet().stream().flatMap(entry -> {
                int userId = entry.getKey();
                List<Integer> friendUserIds = entry.getValue();
                return friendUserIds.isEmpty() ? Stream.empty() : Stream.of(
                    friendUserIds.stream()
                        .map(friendUserId -> brokenFriendshipVariableName(userId, friendUserId))
                        .collect(Collectors.joining(", ", "bin ", ";"))
                );
            })
        );
    }


    // -----------------------------------------------------------------------------------
    // Helpers

    private static Stream<String> addComment(String comment, Stream<String> result) {
        return enableComments
            ? Stream.concat(Stream.of(comment), result)
            : result;
    }

    private static Stream<String> addCommentAndEmptyLine(String comment, Stream<String> result) {
        return enableComments
            ? Stream.of(Stream.of(comment), result, Stream.of("")).flatMap(Function.identity())
            : result;
    }

    private static String userInGroupVariableName(int userId, int numericGroupId) {
        // when changing the format, also adjust LpSolveResultParser.PATTERN
        return "u" + userId + "g" + numericGroupId;
    }

    private static String brokenFriendshipVariableName(int userId, int friendUserId) {
        return "bf" + userId + "_" + friendUserId;
    }
}
