package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class JooqDetachedRecordNegativeTest {
    private static CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(JooqDetachedRecord.class, JooqDetachedRecordNegativeTest.class);
    }

    @Test
    public void testSuppressed() {
        helper().addSourceLines("ExampleSuppressed.java",
            "package bugpatterns;",
            "import de.rptu.cs.exclaim.data.records.UserRecord;",
            "@SuppressWarnings(\"JooqDetachedRecord\")",
            "public class ExampleSuppressed {",
            "  public void example() {",
            "    new UserRecord();",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testUnrelated() {
        helper().addSourceLines("ExampleUnrelated.java",
            "package bugpatterns;",
            "import de.rptu.cs.exclaim.data.records.UserRecord;",
            "public class ExampleUnrelated {",
            "  public void example() {",
            "    new String(\"Hello World!\");",
            "  }",
            "}"
        ).doTest();
    }
}
