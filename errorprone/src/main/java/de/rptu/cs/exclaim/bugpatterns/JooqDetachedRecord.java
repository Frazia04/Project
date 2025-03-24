package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.NewClassTree;
import org.jooq.Record;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

/**
 * Bug checker to detect creation of detached records.
 */
@BugPattern(
    summary = "The Record constructor creates a detached record. Instead, call a create() method on the DAO class, which should return dsl.newRecord(TABLE).",
    severity = ERROR,
    linkType = BugPattern.LinkType.NONE)
public class JooqDetachedRecord extends BugChecker implements NewClassTreeMatcher {
    private static final String FORBIDDEN_CLASS_NAME = Record.class.getName();

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (isSubtype(getType(tree), state.getTypeFromString(FORBIDDEN_CLASS_NAME), state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
