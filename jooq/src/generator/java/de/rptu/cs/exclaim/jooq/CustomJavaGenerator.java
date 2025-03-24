package de.rptu.cs.exclaim.jooq;

import org.jooq.codegen.GeneratorStrategy;
import org.jooq.codegen.GeneratorStrategy.Mode;
import org.jooq.codegen.JavaGenerator;
import org.jooq.codegen.JavaWriter;
import org.jooq.meta.ColumnDefinition;
import org.jooq.meta.DataTypeDefinition;
import org.jooq.meta.Definition;
import org.jooq.meta.EnumDefinition;
import org.jooq.meta.TableDefinition;
import org.jooq.meta.TypedElementDefinition;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class CustomJavaGenerator extends JavaGenerator {
    // -----------------------------------------------------------------------------------------------------------------
    // Better toString() support for Records and POJOs.

    @Override
    protected void generateRecordClassFooter(TableDefinition table, JavaWriter out) {
        super.generateRecordClassFooter(table, out);

        // Records by default do not have a good toString method, so we generate one.
        generateToString(table, Mode.RECORD, out);
    }

    @Override
    protected void generatePojoToString(Definition tableOrUDT, JavaWriter out) {
        if (tableOrUDT instanceof TableDefinition tableDefinition) {
            // The default toString method for records does not contain the field names (only values).
            // We want to have it similar to what Lombok generates, so overwrite it here.
            generateToString(tableDefinition, Mode.POJO, out);
        } else {
            super.generatePojoToString(tableOrUDT, out);
        }
    }

    private void generateToString(TableDefinition table, Mode mode, JavaWriter out) {
        // Mostly stolen from super.generatePojoToString, but we also show the field names (in addition to the values).
        // Additionally, we hide fields with name "password".
        GeneratorStrategy strategy = getStrategy();
        String className = strategy.getJavaClassName(table, mode);

        out.println("@Override");
        out.println("public String toString() {");
        out.println("%s sb = new %s(\"%s(\");", StringBuilder.class, StringBuilder.class, className);

        String separator = "";
        Mode memberMode = (mode == Mode.POJO) ? Mode.POJO : Mode.DEFAULT;
        for (ColumnDefinition column : table.getColumns()) {
            String columnMember = strategy.getJavaMemberName(column, memberMode);
            if (columnMember.equals("password")) {
                out.println("// hidden: " + columnMember);
                continue;
            }

            String columnType = getJavaType(column.getType(resolver(out)), out);
            boolean array = isArrayType(columnType);
            String getterOrMember = mode == Mode.POJO
                ? columnMember
                : strategy.getJavaGetterName(column, mode) + "()";
            if (array && columnType.equals("byte[]"))
                out.println("sb.append(\"%s%s=[binary...]\");", separator, columnMember);
            else if (array)
                out.println("sb.append(\"%s%s=\").append(%s.toString(%s));", separator, columnMember, Arrays.class, getterOrMember);
            else
                out.println("sb.append(\"%s%s=\").append(%s);", separator, columnMember, getterOrMember);

            separator = ", ";
        }

        out.println("sb.append(\")\");");
        out.println("return sb.toString();");
        out.println("}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // If a column is explicitly declared as NULL, then we add a @Nullable annotation on getters. This is different from
    // jOOQs withNullableAnnotation option, because the latter one (from jOOQ 3.18 onwards) adds @Nullable to almost
    // everything since it could be null due to an outer join. That behaviour, however, is not helpful at all.

    private void generateGetterHead(TypedElementDefinition<?> column, JavaWriter out, Mode mode) {
        out.javadoc("Getter for <code>%s</code>.", column.getQualifiedOutputName());
        DataTypeDefinition type = column.getType(resolver(out, mode));
        if (type.isNullable()) {
            out.println("@%s", out.ref(generatedNullableAnnotationType()));
        }
        out.overrideIf(mode != Mode.INTERFACE);
        out.println(
            "public %s %s()" + (mode == Mode.INTERFACE ? ";" : " {"),
            out.ref(getJavaType(type, out, mode)),
            getStrategy().getJavaGetterName(column, mode)
        );
    }

    @Override
    protected void generateInterfaceGetter(TypedElementDefinition<?> column, int index, JavaWriter out) {
        generateGetterHead(column, out, Mode.INTERFACE);
    }

    @Override
    protected void generatePojoGetter(TypedElementDefinition<?> column, int index, JavaWriter out) {
        generateGetterHead(column, out, Mode.POJO);
        out.println("return this.%s;", getStrategy().getJavaMemberName(column, Mode.POJO));
        out.println("}");
    }

    @Override
    protected void generateRecordGetter(TypedElementDefinition<?> column, int index, JavaWriter out) {
        generateGetterHead(column, out, Mode.RECORD);
        String javaType = getJavaType(column.getType(resolver(out, Mode.RECORD)), out, Mode.RECORD);
        if (Object.class.getName().equals(javaType)) {
            out.println("return get(%s);", index);
        } else {
            out.println("return (%s) get(%s);", out.ref(javaType), index);
        }
        out.println("}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Similarly to the getters (see above), we add @Nullable to the setter argument if and only if the column type is
    // explicitly declared as NULL. Additionally, we add special setters that do not touch the changed flag if the
    // current value already equals the value to set.

    @Override
    protected void generateRecordSetter(TypedElementDefinition<?> column, int index, JavaWriter out) {
        GeneratorStrategy strategy = getStrategy();
        String setter = strategy.getJavaSetterName(column, Mode.RECORD);
        String getter = strategy.getJavaGetterName(column, Mode.RECORD);
        String name = column.getQualifiedOutputName();

        DataTypeDefinition dataTypeDefinition = column.getType(resolver(out, Mode.RECORD));
        String type = out.ref(getJavaType(dataTypeDefinition, out, Mode.RECORD));
        if (generateVarargsSetters()) {
            type = SQUARE_BRACKETS.matcher(type).replaceFirst("...");
        }

        List<String> nullableAnnotation = dataTypeDefinition.isNullable()
            ? List.of(out.ref(generatedNullableAnnotationType()))
            : Collections.emptyList();

        // Normal setter
        out.javadoc("Setter for <code>%s</code>.", name);
        out.println("public void %s([[before=@][after= ][%s]]%s value) {", setter, nullableAnnotation, type);
        out.println("set(%s, value);", index);
        out.println("}");

        // Setter that sets the value (and thus marks it as changed) only if it does not equal the current value
        out.javadoc("Setter for <code>%s</code> that does not touch the changed flag if the current value already equals the value to set.\n@return whether the value has been changed", name);
        out.println("public boolean %sIfChanged([[before=@][after= ][%s]]%s value) {", setter, nullableAnnotation, type);
        if (dataTypeDefinition.isArray()) {
            out.println("if (!%s.deepEquals(%s(), value)) {", Arrays.class, getter);
        } else {
            out.println("if (!%s.equals(%s(), value)) {", Objects.class, getter);
        }
        out.println("%s(value);", setter);
        out.println("return true;");
        out.println("}");
        out.println("return false;");
        out.println("}");
    }

    private static final Pattern SQUARE_BRACKETS = Pattern.compile("\\[]$");

    // -----------------------------------------------------------------------------------------------------------------
    // In the Weekday enum, generate helper methods converting to/from a Java DayOfWeek.

    @Override
    protected void generateEnumClassFooter(EnumDefinition e, JavaWriter out) {
        super.generateEnumClassFooter(e, out);
        if (getStrategy().getJavaClassName(e, Mode.ENUM).equals("Weekday")) {
            out.println();
            out.println("public %s toDayOfWeek() {", DayOfWeek.class);
            out.println("return %s.of(ordinal() + 1);", DayOfWeek.class);
            out.println("}");
            out.println();
            out.println("public static Weekday fromDayOfWeek(%s dayOfWeek) {", DayOfWeek.class);
            out.println("return values()[dayOfWeek.getValue() - 1];");
            out.println("}");
        }
    }
}
