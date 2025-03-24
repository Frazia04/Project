/**
 * Subproject: Flyway
 *
 * This subproject contains migrations and callbacks for Flyway, some as plain SQL scripts (shipping as resources), some
 * as Java code (need to be compiled first). We need this separate subproject (instead of contained in app) because
 * compiling the app needs code generated by jOOQ, and jOOQ needs the already compiled Flyway migrations.
 *
 * There is a separate source set for each supported SQL dialect. Common code should be placed in source set main,
 * dialect-specific code in each dialect-specific source set.
 *
 * Furthermore, there is a task condenseH2Migrations that runs all H2 migrations on an empty database and dumps the
 * resulting schema to an SQL script file. That file will be part of the H2 jar archive and speeds up populating a new
 * database at runtime and for testing.
 */

import de.rptu.cs.exclaim.gradle.DatabaseServerProvider
import de.rptu.cs.exclaim.gradle.ErrorPronePlugin.ErrorProneExtension
import de.rptu.cs.exclaim.gradle.JavaExecOutputLogger
import de.rptu.cs.exclaim.gradle.SqlDialect
import de.rptu.cs.exclaim.gradle.Utils

plugins {
    id("exclaim.base")
    id("exclaim.lombok")
    id("exclaim.error-prone")
    id("exclaim.junit")
    id("exclaim.sql-dialect-source-sets-depend-main")
    id("exclaim.sql-dialect-test-source-sets-depend-test")
    id("exclaim.sql-dialect-outgoing-variants")
}

// Define an additional source set to compile classes for the condenseH2Migrations task
sourceSets.register("condenseH2Migrations")

dependencies {
    // Dependencies used in migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.jooq:jooq")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api") // required by jOOQ
    implementation("org.springframework.boot:spring-boot-starter-logging")

    // Some databases require a Flyway plugin
    postgresqlRuntimeOnly("org.flywaydb:flyway-database-postgresql")

    // Dependencies for the condenseH2Migrations task
    "condenseH2MigrationsImplementation"("org.flywaydb:flyway-core")
    "condenseH2MigrationsImplementation"("org.springframework:spring-jdbc")
    "condenseH2MigrationsImplementation"("org.springframework.boot:spring-boot-starter-logging")
    "condenseH2MigrationsRuntimeOnly"("org.codehaus.janino:janino") // logback filter
}

SqlDialect.VALID_DIALECTS.forEach { dialect ->
    // Exclude the *.txt files (for migrations not in main/resources) from jar archives
    tasks.named<Jar>(dialect.jarTaskName) {
        exclude("**/*.txt")
    }

    // Provide database connection properties to test task
    DatabaseServerProvider.applyTo(project, tasks.named<Test>(dialect.testSourceSetName), dialect)
}

// H2: Task to condense all migrations to a single SQL script file. Add generated file to jar archive.
val condenseH2Migrations = tasks.register<JavaExec>("condenseH2Migrations") {
    group = "flyway"
    description = "Condense all H2 migrations to a single SQL script that can be used to populate a fresh H2 database."
    classpath = sourceSets["condenseH2Migrations"].runtimeClasspath + sourceSets["h2"].runtimeClasspath
    mainClass.set("de.rptu.cs.exclaim.flyway.Main")
    JavaExecOutputLogger.applyToSingleThreadedTask(this)
    val condensedMigrationsFile = layout.buildDirectory.get().file("db/exclaim-schema-h2.sql")
    systemProperties["exclaim.output_file"] = condensedMigrationsFile.asFile.path
    outputs.file(condensedMigrationsFile)
    outputs.cacheIf { true }
    Utils.doFirstDelete(this, condensedMigrationsFile)
}
tasks.named<Jar>(SqlDialect.H2.jarTaskName) {
    from(condenseH2Migrations) {
        rename { "de/rptu/cs/exclaim/db/exclaim-schema.sql" }
    }
}
tasks.named("condenseH2MigrationsClasses").configure {
    shouldRunAfter(tasks.h2Classes)
}

// Do not use jOOQ-checker when compiling the tests
sourceSets.test {
    extensions.getByType<ErrorProneExtension>().jooqChecker.set(false)
}
