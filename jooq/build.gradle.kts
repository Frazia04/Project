/**
 * Subproject: jOOQ
 *
 * This subproject migrates a fresh database and executes the jOOQ code generator on it. For H2, an in-memory database
 * is used. For other SQL dialects, we use Testcontainers to start a Docker container with the database server.
 */

import de.rptu.cs.exclaim.gradle.DatabaseServerProvider
import de.rptu.cs.exclaim.gradle.ErrorPronePlugin.ErrorProneExtension
import de.rptu.cs.exclaim.gradle.JavaExecOutputLogger
import de.rptu.cs.exclaim.gradle.LombokPlugin.LombokExtension
import de.rptu.cs.exclaim.gradle.SqlDialect
import de.rptu.cs.exclaim.gradle.SqlDialectPlugin.SQL_DIALECT_ATTRIBUTE
import de.rptu.cs.exclaim.gradle.Utils

plugins {
    idea
    id("exclaim.base")
    id("exclaim.lombok")
    id("exclaim.error-prone")
    id("exclaim.sql-dialect-outgoing-variants")
}

evaluationDependsOn(":flyway")

// Define an additional source set to first compile the jOOQ code generator configuration.
sourceSets.register("generator")

dependencies {
    // For compiling the jOOQ code generator configuration and executing the generator
    "generatorImplementation"("org.jooq:jooq")
    "generatorImplementation"("org.jooq:jooq-codegen")
    "generatorImplementation"("org.flywaydb:flyway-core")
    "generatorImplementation"("org.springframework.boot:spring-boot-starter-logging")
    "generatorRuntimeOnly"("org.codehaus.janino:janino") // logback filter
    "generatorRuntimeOnly"(project(":flyway"))

    // For compiling the generated code
    implementation("org.jooq:jooq")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api") // required by jOOQ
}

SqlDialect.VALID_DIALECTS.forEach { dialect ->
    val generatorRuntimeConfiguration = configurations.register("${dialect.sourceSetName}GeneratorRuntimeClasspath") {
        description = "Runtime classpath for the jOOQ code generator using the ${dialect.name} dialect."
        isVisible = false
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(SQL_DIALECT_ATTRIBUTE, dialect)
        extendsFrom(
            configurations["generatorImplementation"],
            configurations["generatorRuntimeOnly"],
        )
    }
    val generatorTask = tasks.register<JavaExec>("generateJooq${dialect.name}") {
        group = "jooq"
        description = "Executes the jOOQ code generator for the ${dialect.name} dialect."
        classpath = sourceSets["generator"].output + generatorRuntimeConfiguration.get()
        mainClass.set("de.rptu.cs.exclaim.jooq.Main")
        JavaExecOutputLogger.applyToSingleThreadedTask(this)
        val generatedSrcDir = layout.buildDirectory.get().dir("generated-src/${dialect.sourceSetName}/java")
        systemProperties["exclaim.output_directory"] = generatedSrcDir.asFile.path
        outputs.dir(generatedSrcDir)
        outputs.cacheIf { true }
        Utils.doFirstDelete(this, generatedSrcDir) // cleanup from previous run
        idea { module { generatedSourceDirs.add(generatedSrcDir.asFile) } }
    }
    DatabaseServerProvider.applyTo(project, generatorTask, dialect)
    sourceSets.named(dialect.sourceSetName) {
        java.srcDir(generatorTask) // also adds task dependency
        extensions.getByType<LombokExtension>().enabled.set(false) // Lombok is not used in generated code
        extensions.getByType<ErrorProneExtension>().enabled.set(false) // no Error Prone checks for generated code
    }
}
