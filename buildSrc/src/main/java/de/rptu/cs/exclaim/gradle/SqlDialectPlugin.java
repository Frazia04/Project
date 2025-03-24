package de.rptu.cs.exclaim.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * SQL Dialect Plugin
 * <p>
 * This plugin adds support for different SQL dialect variants.
 */
public class SqlDialectPlugin implements Plugin<Project> {
    public static final Attribute<SqlDialect> SQL_DIALECT_ATTRIBUTE = Attribute.of("exclaim.sql-dialect", SqlDialect.class);

    // IntelliJ IDEA sometimes needs to disambiguate. Prefer H2, but log the incident.
    public static class DialectAttributeDisambiguationRule implements AttributeDisambiguationRule<SqlDialect> {
        private final boolean isIntellijIdeaSync = Utils.isIntellijIdeaSync();

        @Override
        public void execute(MultipleCandidatesDetails<SqlDialect> details) {
            if (isIntellijIdeaSync && details.getCandidateValues().contains(SqlDialect.H2)) {
                new Exception("No specific SQL dialect has been requested, will use H2.").printStackTrace(System.out);
                details.closestMatch(SqlDialect.H2);
            }
        }
    }

    @Override
    public void apply(Project project) {
        project.getDependencies().getAttributesSchema().attribute(SQL_DIALECT_ATTRIBUTE);
        project.getDependencies().getAttributesSchema().getMatchingStrategy(SQL_DIALECT_ATTRIBUTE).getDisambiguationRules().add(DialectAttributeDisambiguationRule.class);
    }
}
