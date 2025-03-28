package de.rptu.cs.exclaim.db.migration;

import de.rptu.cs.exclaim.db.Utils;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.Constraint;
import org.jooq.DSLContext;
import org.jooq.ForeignKey;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static de.rptu.cs.exclaim.db.Utils.JOOQ_SETTINGS;

/**
 * Rename all constraints (primary key, foreign key, unique) such that they have deterministic names.
 * <p>
 * Up to now, all migrations created constraints without explicit names. The name will be auto-generated by the database
 * system. To modify a constraint in a later migration, we need to know its name. Furthermore, the constraint name might
 * appear in error messages (e.g. when violating uniqueness). Therefore, deterministic names are helpful.
 */
@Slf4j
public class V25__rename_constraints extends BaseJavaMigration {
    // All primary keys that we have created up to now.
    // We will rename the primary key constraint to pk__tablename.
    private static final String[] KNOWN_PRIMARY_KEYS = {
        "admissions", "annotations", "assignments", "attendance", "comments", "deltapoints", "examgrades",
        "examparticipants", "examresults", "exams", "examtasks", "exercises", "groups", "preferences",
        "results", "sheets", "studentres", "testresult", "unread", "uploads", "users", "user_rights", "warnings"
    };

    // All foreign keys that we have created up to now.
    // Note that we do not yet have two foreign keys with the same source and destination table.
    // We will rename the constraints to fk__sourcetable__destinationtable.
    private static final Map<String, List<String>> KNOWN_FOREIGN_KEYS = Map.ofEntries(
        Map.entry("admissions", List.of("exercises")),
        Map.entry("annotations", List.of("uploads")),
        Map.entry("assignments", List.of("sheets")),
        Map.entry("attendance", List.of("sheets")),
        Map.entry("comments", List.of("sheets")),
        Map.entry("deltapoints", List.of("sheets")),
        Map.entry("examgrades", List.of("exams")),
        Map.entry("examparticipants", List.of("exams")),
        Map.entry("examresults", List.of("examparticipants", "exams", "examtasks")),
        Map.entry("exams", List.of("exercises")),
        Map.entry("examtasks", List.of("exams")),
        Map.entry("groups", List.of("exercises")),
        Map.entry("preferences", List.of("exercises")),
        Map.entry("results", List.of("assignments")),
        Map.entry("sheets", List.of("exercises")),
        Map.entry("studentres", List.of("sheets")),
        Map.entry("testresult", List.of("assignments", "sheets")),
        Map.entry("unread", List.of("uploads")),
        Map.entry("uploads", List.of("assignments", "sheets")),
        Map.entry("user_rights", List.of("exercises", "users")),
        Map.entry("warnings", List.of("uploads"))
    );

    // All unique constraints that we have created up to now.
    // Note that we do not yet have unique keys spanning over multiple columns.
    // We will rename the constraints to u__tablename__columnname.
    private static final Map<String, List<String>> KNOWN_UNIQUE_CONSTRAINTS = Map.of(
        "users", List.of("username", "studentid")
    );

    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public void migrate(Context context) {
        DSLContext ctx = DSL.using(context.getConnection(), JOOQ_SETTINGS);

        log.debug("Collecting current tables metadata");
        String schema = Utils.getSchema(context);
        List<Schema> schemas = ctx.meta().getSchemas(schema);
        if (schemas.isEmpty()) {
            throw new IllegalStateException("Database schema " + schema + " not found");
        }
        List<Table<?>> result = schemas.get(0).getTables();
        Map<String, Table<?>> tables = new HashMap<>(result.size());
        for (Table<?> table : result) {
            tables.put(table.getName().toLowerCase(Locale.ROOT), table);
        }

        log.debug("Renaming primary key constraints");
        for (String tableName : KNOWN_PRIMARY_KEYS) {
            Table<?> table = Objects.requireNonNull(tables.get(tableName), "Table " + tableName + " not found");
            UniqueKey<?> primaryKey = Objects.requireNonNull(table.getPrimaryKey(), "No primary key for table " + tableName);
            Constraint constraint = primaryKey.constraint();
            String oldName = constraint.getName();
            String newName = "pk__" + tableName;
            if (!oldName.equalsIgnoreCase(newName)) {
                log.debug("Renaming primary key constraint for table {}: {} -> {}", tableName, oldName, newName);
                ctx.alterTable(primaryKey.getTable()).renameConstraint(constraint).to(DSL.unquotedName(newName)).execute();
            }
        }

        log.debug("Renaming foreign key constraints");
        KNOWN_FOREIGN_KEYS.forEach((source, destinations) -> {
            Table<?> sourceTable = Objects.requireNonNull(tables.get(source), "Table " + source + " not found");
            Map<String, ForeignKey<?, ?>> foreignKeys = new HashMap<>();
            for (ForeignKey<?, ?> foreignKey : sourceTable.getReferences()) {
                String destination = foreignKey.getKey().getTable().getName();
                if (foreignKeys.put(destination.toLowerCase(Locale.ROOT), foreignKey) != null) {
                    throw new IllegalStateException("There are multiple foreign keys from " + source + " to " + destination);
                }
            }
            for (String destination : destinations) {
                ForeignKey<?, ?> foreignKey = Objects.requireNonNull(foreignKeys.get(destination), "Foreign key from " + source + " to " + destination + " not found");
                Constraint constraint = foreignKey.constraint();
                String oldName = constraint.getName();
                String newName = "fk__" + source + "__" + destination;
                if (!oldName.equalsIgnoreCase(newName)) {
                    log.debug("Renaming foreign key from {} to {}: {} -> {}", source, destination, oldName, newName);
                    ctx.alterTable(foreignKey.getTable()).renameConstraint(constraint).to(DSL.unquotedName(newName)).execute();
                }
            }
        });

        log.debug("Renaming unique constraints");
        KNOWN_UNIQUE_CONSTRAINTS.forEach((tableName, columns) -> {
            Table<?> table = Objects.requireNonNull(tables.get(tableName), "Table " + tableName + " not found");
            Map<String, UniqueKey<?>> uniqueKeys = new HashMap<>();
            for (UniqueKey<?> uniqueKey : table.getUniqueKeys()) {
                TableField<?, ?>[] fields = uniqueKey.getFieldsArray();
                if (fields.length == 1) {
                    uniqueKeys.put(fields[0].getName().toLowerCase(Locale.ROOT), uniqueKey);
                }
            }
            for (String column : columns) {
                UniqueKey<?> uniqueKey = Objects.requireNonNull(uniqueKeys.get(column), "No unique constraint found on column " + column + " in table " + tableName);
                Constraint constraint = uniqueKey.constraint();
                String oldName = constraint.getName();
                String newName = "U__" + tableName + "__" + column;
                if (!oldName.equalsIgnoreCase(newName)) {
                    log.debug("Renaming unique constraint on {} in {}: {} -> {}", column, table, oldName, newName);
                    ctx.alterTable(uniqueKey.getTable()).renameConstraint(constraint).to(DSL.unquotedName(newName)).execute();
                }
            }
        });
    }
}
