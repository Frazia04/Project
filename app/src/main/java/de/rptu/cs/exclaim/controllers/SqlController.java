package de.rptu.cs.exclaim.controllers;

import de.rptu.cs.exclaim.monitoring.MetricsService;
import de.rptu.cs.exclaim.security.AccessChecker;
import de.rptu.cs.exclaim.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Allow;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.rptu.cs.exclaim.controllers.ControllerUtils.MessageType;
import static de.rptu.cs.exclaim.controllers.ControllerUtils.addMessage;

/**
 * Allow admins to execute SQL queries.
 */
@Controller
@PreAuthorize("@accessChecker.isAdmin()")
@Slf4j
@RequiredArgsConstructor
@Allow
@Allow.PlainSQL
public class SqlController {
    private final MetricsService metricsService;
    private final AccessChecker accessChecker;
    private final TransactionTemplate transactionTemplate;
    private final DSLContext ctx;

    private final Pattern PATTERN_UPDATE = Pattern.compile("UPDATE\\s+(?<table>\\S+)\\s.*WHERE(?<where>.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final Pattern PATTERN_DELETE = Pattern.compile("DELETE FROM\\s+(?<table>\\S+)\\s+WHERE(?<where>.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final Pattern PATTERN_INSERT = Pattern.compile("INSERT INTO.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @GetMapping("/sql")
    public String getSqlPage() {
        metricsService.registerAccess();
        return "sql";
    }

    @PostMapping("/sql/query")
    public String submitQuery(@RequestParam String query, Model model) {
        metricsService.registerAccess();
        model.addAttribute("query", query);
        if (log.isDebugEnabled()) {
            log.debug("Admin user {} is executing query:\n{}", accessChecker.getUser(), query);
        }

        // To be sure that the query does not perform any updates, we wrap it in a transaction that is rolled back.
        transactionTemplate.execute((transactionStatus) -> {
            try {
                Tuple2<String[], List<Object[]>> tuple = executeQuery(query);
                model.addAttribute("columns", tuple.v1);
                model.addAttribute("rows", JsonUtils.toJson(tuple.v2));
            } catch (Exception e) {
                log.debug("Query failed", e);
                addMessage(MessageType.ERROR, getErrorMessage(e), model);
            } finally {
                transactionStatus.setRollbackOnly();
            }
            return null;
        });
        return "sql";
    }

    @PostMapping("/sql/update")
    public String submitUpdate(@RequestParam String query, @RequestParam("expected-updates") int expectedUpdates, Model model) {
        metricsService.registerAccess();
        model.addAttribute("query", query);
        if (log.isDebugEnabled()) {
            log.debug("Admin user {} is executing update:\n{}", accessChecker.getUser(), query);
        }
        try {
            if (PATTERN_INSERT.matcher(query).matches()) {
                // INSERT
                transactionTemplate.execute((transactionStatus) -> {
                    int affectedRows = ctx.execute(query);
                    if (affectedRows == expectedUpdates) {
                        log.info("Admin user {} executed INSERT statement with {} rows affected:\n{}", accessChecker.getUser(), affectedRows, query);
                        addMessage(MessageType.SUCCESS, "Database was updated with " + affectedRows + " rows affected.", model);
                    } else {
                        transactionStatus.setRollbackOnly();
                        addMessage(MessageType.ERROR, "ERROR: suggested number of affected rows was not correct (it would have affected " + affectedRows + " rows)", model);
                    }
                    return null;
                });
            } else {
                Matcher matcher;
                if ((matcher = PATTERN_UPDATE.matcher(query)).matches() || (matcher = PATTERN_DELETE.matcher(query)).matches()) {
                    // UPDATE/DELETE
                    String table = matcher.group("table");
                    String where = matcher.group("where");
                    String selectQuery = "SELECT * FROM " + table + " WHERE " + where;
                    transactionTemplate.execute((transactionStatus) -> {
                        Tuple2<String[], List<Object[]>> tuple = executeQuery(selectQuery);
                        String[] columns = tuple.v1;
                        List<Object[]> rowsBefore = tuple.v2;
                        int affectedRows = ctx.execute(query);
                        if (affectedRows == expectedUpdates) {
                            tuple = executeQuery(selectQuery);
                            List<Object[]> rowsAfter = tuple.v2;
                            if (log.isInfoEnabled()) {
                                log.info("Admin user {} executed UPDATE/DELETE statement with {} rows affected:\n{}\nColumns: {}\nBefore: {} \nAfter:  {}",
                                    accessChecker.getUser(), affectedRows, query, columns,
                                    rowsBefore.stream().map(Arrays::toString).toArray(),
                                    rowsAfter.stream().map(Arrays::toString).toArray());
                            }
                            addMessage(MessageType.SUCCESS, "Database was updated with " + affectedRows + " rows affected.", model);
                            model.addAttribute("columns", columns);
                            model.addAttribute("rows", JsonUtils.toJson(rowsBefore));
                            model.addAttribute("rowsAfter", JsonUtils.toJson(rowsAfter));
                        } else {
                            transactionStatus.setRollbackOnly();
                            addMessage(MessageType.ERROR, "ERROR: suggested number of affected rows was not correct (it would have affected " + affectedRows + " rows)", model);
                        }
                        return null;
                    });
                } else {
                    addMessage(MessageType.ERROR, "This kind of update is not supported.", model);
                }
            }
        } catch (Exception e) {
            log.debug("Query failed", e);
            addMessage(MessageType.ERROR, getErrorMessage(e), model);
        }
        return "sql";
    }

    private Tuple2<String[], List<Object[]>> executeQuery(String query) {
        // We explicitly use Statement.executeQuery instead of executing the query through jOOQ, because at least the
        // JDBC driver for H2 prevents update queries when using Statement.executeQuery.
        // But we still want to use jOOQ in order to not fiddle around with the ResultSetMetaData (column names).
        return ctx.connectionResult(connection -> {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                Result<Record> result = ctx.fetch(rs);
                return new Tuple2<>(
                    Arrays.stream(result.fields()).map(Field::getName).toArray(String[]::new),
                    result.map(Record::intoArray)
                );
            }
        });
    }

    private String getErrorMessage(Exception e) {
        String message = e.getClass().getName();
        String m = e.getMessage();
        if (m != null) message += ": " + m;
        Throwable cause = e.getCause();
        if (cause != null) {
            message += "\nCaused by " + cause.getClass().getName();
            m = cause.getMessage();
            if (m != null) message += ": " + m;
        }
        return message;
    }
}
