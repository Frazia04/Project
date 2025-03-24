package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class JooqExceptionNegativeTest {
    private static CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(JooqException.class, JooqExceptionNegativeTest.class);
    }

    @Test
    public void testCatchSuppressed() {
        helper().addSourceLines("ExampleSuppressed.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataAccessException;",
            "public class ExampleSuppressed {",
            "  public void example() {",
            "    try {",
            "    } catch (@SuppressWarnings(\"JooqException\") DataAccessException e) {",
            "    }",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testCatchUnrelated() {
        helper().addSourceLines("ExampleCatchUnrelated.java",
            "package bugpatterns;",
            "public class ExampleCatchUnrelated {",
            "  public void example() {",
            "    try {",
            "    } catch (RuntimeException e) {",
            "    }",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testInstanceOfSuppressed() {
        helper().addSourceLines("ExampleInstanceOfSuppressed.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataAccessException;",
            "@SuppressWarnings(\"JooqException\")",
            "public class ExampleInstanceOfSuppressed {",
            "  public boolean example(Exception e) {",
            "    return e instanceof DataAccessException;",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testInstanceOfUnrelated() {
        helper().addSourceLines("ExampleInstanceOfUnrelated.java",
            "package bugpatterns;",
            "public class ExampleInstanceOfUnrelated {",
            "  public boolean example(Exception e) {",
            "    return e instanceof RuntimeException;",
            "  }",
            "}"
        ).doTest();
    }
}
