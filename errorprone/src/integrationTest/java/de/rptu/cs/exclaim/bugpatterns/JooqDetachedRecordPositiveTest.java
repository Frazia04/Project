package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class JooqDetachedRecordPositiveTest {
    private static CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(JooqDetachedRecord.class, JooqDetachedRecordPositiveTest.class);
    }

    @Test
    public void testConstructEmpty() {
        helper().addSourceLines("ExampleConstructEmpty.java",
            "package bugpatterns;",
            "import de.rptu.cs.exclaim.data.records.UserRecord;",
            "public class ExampleConstructEmpty {",
            "  public void example() {",
            "    // BUG: Diagnostic contains: JooqDetachedRecord",
            "    new UserRecord();",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testConstructInitialized() {
        helper().addSourceLines("ExampleConstructInitialized.java",
            "package bugpatterns;",
            "import de.rptu.cs.exclaim.data.records.UserRecord;",
            "public class ExampleConstructInitialized {",
            "  public void example() {",
            "    // BUG: Diagnostic contains: JooqDetachedRecord",
            "    new UserRecord(42, \"Firstname\", \"Lastname\", \"username\", \"123456\", \"secret\", \"user@example.com\", false, null, null, null);",
            "  }",
            "}"
        ).doTest();
    }
}
