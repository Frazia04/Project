package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.CatchTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.InstanceOfTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import org.jooq.exception.DataAccessException;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

/**
 * Bug checker to detect usage of {@link org.jooq.exception.DataAccessException}.
 */
@BugPattern(
    summary = "Do not use jOOQ exceptions, they are translated to Spring exceptions.",
    severity = ERROR,
    linkType = BugPattern.LinkType.NONE)
public class JooqException extends BugChecker implements CatchTreeMatcher, InstanceOfTreeMatcher {
    private static final String FORBIDDEN_CLASS_NAME = DataAccessException.class.getName();

    @Override
    public Description matchCatch(CatchTree tree, VisitorState state) {
        VariableTree parameter = tree.getParameter();
        if (!isSuppressed(parameter, state)) {
            visitType(parameter.getType(), state);
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchInstanceOf(InstanceOfTree tree, VisitorState state) {
        visitType(tree.getType(), state);
        return Description.NO_MATCH;
    }

    private void visitType(Tree tree, VisitorState state) {
        if (tree instanceof UnionTypeTree unionTypeTree) {
            for (Tree alternate : unionTypeTree.getTypeAlternatives()) {
                visitType(alternate, state);
            }
        } else {
            if (isSubtype(getType(tree), state.getTypeFromString(FORBIDDEN_CLASS_NAME), state)) {
                state.reportMatch(describeMatch(tree));
            }
        }
    }
}
