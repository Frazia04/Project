package de.rptu.cs.exclaim.db.migration;

import de.rptu.cs.exclaim.db.TestUtils;
import org.junit.jupiter.api.Test;

class AllMigrationsTest {
    @Test
    void testMigrations() throws Exception {
        TestUtils.testFlyway((dataSource, flyway) -> flyway.load().migrate());
    }
}
