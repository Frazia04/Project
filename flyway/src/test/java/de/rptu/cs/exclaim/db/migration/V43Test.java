package de.rptu.cs.exclaim.db.migration;

import de.rptu.cs.exclaim.db.TestUtils;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class V43Test {
    private static final String VERSION = "43";

    @Test
    void testMigration() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> {
            flyway.target(VERSION).load().migrate();
            DSLContext ctx = DSL.using(dataSource.getConnection());
            String query = "INSERT INTO users (firstname, lastname, username, password, email, samlid) VALUES ('John', 'Doe', ?, ?, 'j.doe@example.com', ?)";
            ctx.batch(query)
                // username, password, samlid
                .bind("user1", "hash", null)
                .bind("user2", "hash", null)
                .bind("user3", "hash", "saml3")
                .bind(null, null, "saml4")
                .bind(null, null, "saml5")
                .bind("user6", null, "saml6")
                .execute();

            Object[][] violating = {
                // username, password, samlid
                {null, null, null}, // check: neither samlid nor password
                {null, "hash", null}, // check: password without username
                {null, "hash", "saml7"}, // check: password without username
                {"user1", "hash", null}, // unique: username
                {null, null, "saml3"}, // unique: samlid
            };
            for (Object[] bindings : violating) {
                assertThrows(DataAccessException.class, () -> ctx.execute(query, bindings));
            }
        });
    }
}
