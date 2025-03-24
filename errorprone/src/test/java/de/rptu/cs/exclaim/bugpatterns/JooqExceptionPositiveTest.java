package de.rptu.cs.exclaim.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class JooqExceptionPositiveTest {
    private static CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(JooqException.class, JooqExceptionPositiveTest.class);
    }

    @Test
    public void testCatch() {
        helper().addSourceLines("ExampleCatch.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataAccessException;",
            "public class ExampleCatch {",
            "  public void example() {",
            "    try {",
            "    // BUG: Diagnostic contains: JooqException",
            "    } catch (DataAccessException e) {",
            "    }",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testCatchSubtype() {
        helper().addSourceLines("ExampleCatchSubtype.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataTypeException;",
            "public class ExampleCatchSubtype {",
            "  public void example() {",
            "    try {",
            "    // BUG: Diagnostic contains: JooqException",
            "    } catch (DataTypeException e) {",
            "    }",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testCatchUnion() {
        helper().addSourceLines("ExampleCatchUnion.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataAccessException;",
            "public class ExampleCatchUnion {",
            "  public void example() {",
            "    try {",
            "    // BUG: Diagnostic contains: JooqException",
            "    } catch (Error | DataAccessException e) {",
            "    }",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testInstanceOf() {
        helper().addSourceLines("ExampleInstanceOf.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataAccessException;",
            "public class ExampleInstanceOf {",
            "  public boolean example(Exception e) {",
            "    // BUG: Diagnostic contains: JooqException",
            "    return e instanceof DataAccessException;",
            "  }",
            "}"
        ).doTest();
    }

    @Test
    public void testInstanceOfSubtype() {
        helper().addSourceLines("ExampleInstanceOfSubtype.java",
            "package bugpatterns;",
            "import org.jooq.exception.DataTypeException;",
            "public class ExampleInstanceOfSubtype {",
            "  public boolean example(Exception e) {",
            "    // BUG: Diagnostic contains: JooqException",
            "    return e instanceof DataTypeException;",
            "  }",
            "}"
        ).doTest();
    }
}
