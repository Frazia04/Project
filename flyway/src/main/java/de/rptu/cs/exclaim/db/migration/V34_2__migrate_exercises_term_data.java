package de.rptu.cs.exclaim.db.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.rptu.cs.exclaim.db.Utils.JOOQ_SETTINGS;

/**
 * Migrating the term data from String to Integer (year) + Enum (summer/winter) requires more sophisticated parsing not
 * possible in a plain sql query.
 */
@Slf4j
public class V34_2__migrate_exercises_term_data extends BaseJavaMigration {
    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public void migrate(Context context) {
        DSLContext ctx = DSL.using(context.getConnection(), JOOQ_SETTINGS);

        // Regular expressions to parse previous term strings
        Pattern ssPattern = Pattern.compile("(?:Sommersemester|SS) ?([0-9]{2}(?:[0-9]{2})?)", Pattern.CASE_INSENSITIVE);
        Pattern wsPattern = Pattern.compile("(?:Wintersemester|WS) ?([0-9]{2}(?:[0-9]{2})?)/([0-9]{2}(?:[0-9]{2})?)", Pattern.CASE_INSENSITIVE);

        // Fetch current exercises
        Result<Record> result = ctx.fetch("SELECT id, term_comment FROM exercises FOR UPDATE");

        // Collect updates
        List<Object[]> updates = new ArrayList<>(result.size());
        for (Record record : result) {
            String id = record.get(0, String.class);
            String termComment = record.get(1, String.class);
            int year;
            String term;
            Matcher m = ssPattern.matcher(termComment);
            if (m.matches()) {
                term = "SUMMER";
                year = Integer.parseInt(m.group(1));
                if (year <= 99) year += 2000;
            } else {
                m = wsPattern.matcher(termComment);
                if (m.matches()) {
                    term = "WINTER";
                    year = Integer.parseInt(m.group(1));
                    if (year <= 99) year += 2000;
                    int year2 = Integer.parseInt(m.group(2));
                    if (year2 <= 99) year2 += 2000;
                    if (year2 - year != 1) {
                        log.warn("Cannot migrate term string \"{}\" (inconsistent year)", termComment);
                        continue;
                    }
                } else {
                    log.warn("Cannot migrate term string \"{}\" (unable to parse)", termComment);
                    continue;
                }
            }

            // Save new values for use in batched update below
            log.debug("Migrating term string \"{}\" to {} {}", termComment, term, year);
            updates.add(new Object[]{year, term, id});
        }

        // Execute updates
        if (!updates.isEmpty()) {
            ctx.batch(DSL.query(ctx.dialect() == SQLDialect.POSTGRES
                    ? "UPDATE exercises SET \"year\" = ?, term = ?::t_term, term_comment = '' WHERE id = ?"
                    : "UPDATE exercises SET \"year\" = ?, term = ?        , term_comment = '' WHERE id = ?"
                ))
                .bind(updates.toArray(Object[][]::new))
                .execute();
        }
    }
}
