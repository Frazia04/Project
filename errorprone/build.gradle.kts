/**
 * Subproject: Error Prone
 *
 * We use the Error Prone static code analysis tool for our Java source files.
 * This subproject contains custom bug patterns.
 */

import de.rptu.cs.exclaim.gradle.ErrorPronePlugin
import de.rptu.cs.exclaim.gradle.ErrorPronePlugin.ErrorProneExtension
import de.rptu.cs.exclaim.gradle.SqlDialect
import de.rptu.cs.exclaim.gradle.SqlDialectPlugin.SQL_DIALECT_ATTRIBUTE

plugins {
    id("exclaim.base")
    id("exclaim.error-prone")
    id("exclaim.junit-integration-tests")
    id("exclaim.sql-dialect")
}

evaluationDependsOn(":jooq")

dependencies {
    // Dependencies for compiling Error Prone bug patterns
    compileOnly("com.google.errorprone:error_prone_core")

    // Additional dependencies for our custom bug patterns
    implementation("org.jooq:jooq")

    // Dependencies for tests
    testImplementation("com.google.errorprone:error_prone_test_helpers")
    integrationTestImplementation("com.google.errorprone:error_prone_test_helpers")
    integrationTestRuntimeOnly(project(":jooq")) // JooqDetachedRecord: new UserRecord()
}

// Let the integrationTestRuntimeOnly dependency defined above use the H2 variant
configurations.integrationTestRuntimeClasspath {
    attributes.attribute(SQL_DIALECT_ATTRIBUTE, SqlDialect.H2)
}

tasks.withType<Test>().configureEach {
    // Expose the runtime classpath in the CLASSPATH environment variable such that the compiler which compiles the test
    // data will find all required classes. Put in doFirst block to not resolve the classpath at configuration time.
    doFirst {
        environment["CLASSPATH"] = classpath.joinToString(separator = ";")
    }

    // The tests run a compiler with Error Prone, so we need to add JVM arguments.
    jvmArgumentProviders.add { ErrorPronePlugin.COMPILER_JVM_ARGS }
}

// Error Prone (when compiling this subproject): We do not need jOOQ-checker
sourceSets.configureEach {
    extensions.getByType<ErrorProneExtension>().jooqChecker.set(false)
}

// When compiling our bug patterns, we need to export internal jdk compiler packages used by Error Prone.
tasks.compileJava {
    options.compilerArgs.addAll(ErrorPronePlugin.COMPILER_ARGS)
}
