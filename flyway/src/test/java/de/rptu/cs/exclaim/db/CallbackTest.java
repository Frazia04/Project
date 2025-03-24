package de.rptu.cs.exclaim.db;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallbackTest {
    @Test
    void testRenameFlywayTable() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            Connection conn = dataSource.getConnection();
            String schema = conn.getSchema();
            flyway.target("1").load().migrate();
            DSLContext ctx = DSL.using(conn);
            ctx.execute("ALTER TABLE \"flyway_schema_history\" RENAME TO \"schema_version\"");
            flyway.load().validate();
            Set<String> result = ctx.meta().filterSchemas(s -> s.getName().equals(schema))
                .getTables().stream()
                .map(t -> t.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
            assertEquals(Set.of("exercises", "flyway_schema_history"), result);
        });
    }

    @Test
    void testFixChecksumOnValidate() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target("23").load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            assertEquals(1, ctx.execute("UPDATE \"flyway_schema_history\" SET \"checksum\" = ? WHERE \"version\" = ?", -168976389, "22"));
            flyway.load().validate();
            int checksum = ctx.fetchSingle("SELECT \"checksum\" FROM \"flyway_schema_history\" WHERE \"version\" = ?", "22").get(0, int.class);
            assertEquals(1143217192, checksum);
        });
    }

    @Test
    void testFixChecksumOnMigrate() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target("23").load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            assertEquals(1, ctx.execute("UPDATE \"flyway_schema_history\" SET \"checksum\" = ? WHERE \"version\" = ?", -168976389, "22"));
            flyway.validateOnMigrate(false).load().migrate();
            int checksum = ctx.fetchSingle("SELECT \"checksum\" FROM \"flyway_schema_history\" WHERE \"version\" = ?", "22").get(0, int.class);
            assertEquals(1143217192, checksum);
        });
    }

    @Test
    void testFixChecksumOnValidateWithCustomSchemas() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target("23").schemas("CUSTOM").load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            assertEquals(1, ctx.execute("UPDATE \"CUSTOM\".\"flyway_schema_history\" SET \"checksum\" = ? WHERE \"version\" = ?", -168976389, "22"));
            flyway.load().validate();
            int checksum = ctx.fetchSingle("SELECT \"checksum\" FROM \"CUSTOM\".\"flyway_schema_history\" WHERE \"version\" = ?", "22").get(0, int.class);
            assertEquals(1143217192, checksum);
        });
    }

    @Test
    void testFixChecksumOnValidateWithCustomDefaultSchema() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target("23").schemas("CUSTOM2", "CUSTOM").defaultSchema("CUSTOM").load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            assertEquals(1, ctx.execute("UPDATE \"CUSTOM\".\"flyway_schema_history\" SET \"checksum\" = ? WHERE \"version\" = ?", -168976389, "22"));
            flyway.load().validate();
            int checksum = ctx.fetchSingle("SELECT \"checksum\" FROM \"CUSTOM\".\"flyway_schema_history\" WHERE \"version\" = ?", "22").get(0, int.class);
            assertEquals(1143217192, checksum);
        });
    }

    @Test
    void testRenameFlywayTableAndFixChecksumOnValidateWithCustomDefaultSchema() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target("23").schemas("CUSTOM2", "CUSTOM").defaultSchema("CUSTOM").load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            assertEquals(1, ctx.execute("UPDATE \"CUSTOM\".\"flyway_schema_history\" SET \"checksum\" = ? WHERE \"version\" = ?", -168976389, "22"));
            ctx.execute("ALTER TABLE \"CUSTOM\".\"flyway_schema_history\" RENAME TO \"schema_version\"");
            flyway.load().validate();
            int checksum = ctx.fetchSingle("SELECT \"checksum\" FROM \"CUSTOM\".\"flyway_schema_history\" WHERE \"version\" = ?", "22").get(0, int.class);
            assertEquals(1143217192, checksum);
        });
    }
}
