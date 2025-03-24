package de.rptu.cs.exclaim.jooq;

import org.jooq.codegen.DefaultGeneratorStrategy;
import org.jooq.meta.ColumnDefinition;
import org.jooq.meta.Definition;
import org.jooq.meta.ForeignKeyDefinition;
import org.jooq.meta.InverseForeignKeyDefinition;
import org.jooq.meta.TableDefinition;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class CustomGeneratorStrategy extends DefaultGeneratorStrategy {
    // -----------------------------------------------------------------------------------------------------------------
    // Mapper functions to change how table and column names are represented in Java code.

    /**
     * Mapper function for table names, used for the names of generated classes.
     *
     * @param name the original name of the database table (in lower-case)
     * @return an updated database table name that is used for the name generation by jOOQ
     */
    private static String mapTableName(String name) {
        return name
            // remove plurals
            .replaceAll("s($|_)", "$1")

            // add underscores in composite words -> results in CamelCase names
            .replaceAll("^(exam|group|student|team|test)([^_].*)$", "$1_$2");
    }

    /**
     * Mapper function for column names, used for the names of generated classes.
     *
     * @param name      the original name of the database column (in lower-case)
     * @param tableName the original name of the database table for that column (in lower-case)
     * @return an updated database column name that is used for the name generation by jOOQ
     */
    private static String mapColumnName(String name, String tableName) {
        // Apply hardcoded replacements, use it if defined.
        String replacement = Map.of(
            // Mappings per table
            "annotations", Map.of(
                "annotationobj", "annotation_obj"
            ),
            "assignments", Map.of(
                "showstatistics", "show_statistics"
            ),
            "teamresults", Map.of(
                "hidecomments", "hide_comments",
                "hidepoints", "hide_points"
            ),
            "testresult", Map.of(
                "requestnr", "request_nr"
            ),
            "warnings", Map.of(
                "infourl", "info_url"
            )
        ).getOrDefault(tableName, Map.of()).get(name);
        if (replacement != null) {
            return replacement;
        }

        // Add underscore before "id" -> results in CamelCase names
        name = name.replaceAll("([^_])id$", "$1_id");

        // Add _id suffix for common names referencing foreign keys
        if (Set.of("exercise", "user", "group", "sheet", "assignment").contains(name)) {
            name += "_id";
        }

        // Column id in table exercise -> exercise_id (similar for group, sheet, ...)
        if (name.equals("id")) {
            name = mapTableName(tableName) + "_id";
        }

        return name;
    }

    /**
     * Mapper function for enum names, used for the names of generated classes.
     *
     * @param name the original name of the database enum (in lower-case)
     * @return an updated database enum name that is used for the name generation by jOOQ
     */
    private static String mapEnumName(String name) {
        // PostgreSql style enums (global per schema)
        if (name.startsWith("t_")) {
            // Remove prefix
            name = name.substring(2);

            // Apply replacements
            return Map.of(
                "group_preference", "group_preference_option" // clashes with POJO name
            ).getOrDefault(name, name);
        }

        // H2 enums (local per table)
        return Map.of(
            "studentresults_attended", "attendance",
            "exercises_term", "term",
            "exercises_group_join", "group_join",
            "groups_day", "weekday",
            "grouppreferences_preference", "group_preference_option",
            "background_jobs_type", "background_job_type"
        ).getOrDefault(name, name);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Overwrite some naming conventions, mostly by using the mappers from above.

    @Override
    public String getJavaPackageName(Definition definition, Mode mode) {
        return switch (mode) {
            case POJO -> "de.rptu.cs.exclaim.data";
            case INTERFACE -> "de.rptu.cs.exclaim.data.interfaces";
            case RECORD -> "de.rptu.cs.exclaim.data.records";
            default ->
                // de.rptu.cs.exclaim.schema.*
                super.getJavaPackageName(definition, mode);
        };
    }

    @Override
    public String getJavaClassName(Definition definition, Mode mode) {
        return switch (mode) {
            case RECORD, POJO, INTERFACE -> super.getJavaClassName(applyTableNameMapping(definition), mode);
            case ENUM -> super.getJavaClassName(applyEnumNameMapping(definition), mode);
            default -> super.getJavaClassName(definition, mode);
        };
    }

    @Override
    public String getJavaMemberName(Definition definition, Mode mode) {
        return super.getJavaMemberName(applyColumnNameMapping(definition), mode);
    }

    @Override
    public String getJavaGetterName(Definition definition, Mode mode) {
        return super.getJavaGetterName(applyColumnNameMapping(definition), mode);
    }

    @Override
    public String getJavaSetterName(Definition definition, Mode mode) {
        return super.getJavaSetterName(applyColumnNameMapping(definition), mode);
    }

    @Override
    public String getJavaMethodName(Definition definition, Mode mode) {
        return super.getJavaMethodName(
            definition instanceof TableDefinition
                ? applyTableNameMapping(definition)
                : definition,
            mode
        );
    }

    @Override
    public String getJavaIdentifier(Definition definition) {
        // Do not prefix foreign key identifiers. For H2, foreign key names must be unique per schema. Since we use the
        // same identifiers in PostgreSQL, we do not need to the PostgreSQL workaround from super.
        if (definition instanceof ForeignKeyDefinition || definition instanceof InverseForeignKeyDefinition) {
            return definition.getOutputName().toUpperCase(Locale.ROOT);
        }
        return super.getJavaIdentifier(definition);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Make sure that we do not depend on Locale.getDefault()

    public CustomGeneratorStrategy() {
        super.setTargetLocale(Locale.ROOT);
    }

    @Override
    public void setTargetLocale(Locale targetLocale) {
        // ignore
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers to apply the mapper functions at the top of this file.

    private static Definition applyTableNameMapping(Definition definition) {
        return proxyDefinition(definition, CustomGeneratorStrategy::mapTableName);
    }

    private static Definition applyColumnNameMapping(Definition definition) {
        if (definition instanceof ColumnDefinition) {
            List<Definition> definitionPath = definition.getDefinitionPath();
            String tableName = definitionPath.get(definitionPath.size() - 2).getOutputName().toLowerCase(Locale.ROOT);
            return proxyDefinition(definition, name -> mapColumnName(name, tableName));
        }
        return definition;
    }

    private static Definition applyEnumNameMapping(Definition definition) {
        return proxyDefinition(definition, CustomGeneratorStrategy::mapEnumName);
    }

    // Wrap a given Definition object into a Proxy that delegates all method calls.
    // Calls to getOutputName() are intercepted and the result is passed to the mapper function.
    private static Definition proxyDefinition(Definition definition, Function<String, String> mapper) {
        return (Definition) Proxy.newProxyInstance(
            CustomGeneratorStrategy.class.getClassLoader(),
            new Class<?>[]{Definition.class},
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("getOutputName")) {
                    return mapper.apply(definition.getOutputName().toLowerCase(Locale.ROOT));
                }
                return method.invoke(definition, methodArgs);
            });
    }
}
