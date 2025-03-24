/**
 * Subproject: App
 *
 * This subproject builds the main Spring Boot application.
 */

import de.rptu.cs.exclaim.gradle.ErrorPronePlugin.ErrorProneExtension
import de.rptu.cs.exclaim.gradle.SqlDialect
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    idea
    id("exclaim.base")
    id("exclaim.lombok")
    id("exclaim.error-prone")
    id("exclaim.junit")
    id("exclaim.sql-dialect-source-sets-include-main")
    id("exclaim.sql-dialect-spring-boot")
    id("exclaim.sql-dialect-test-source-sets-include-test")
    id("exclaim.end-to-end-tests")
}

evaluationDependsOn(":api")
evaluationDependsOn(":docs")
evaluationDependsOn(":errorprone")
evaluationDependsOn(":flyway")
evaluationDependsOn(":frontend")
evaluationDependsOn(":jooq")
evaluationDependsOn(":proof-tree-generator")
evaluationDependsOn(":recursion-tutor")

springBoot {
    // The Main class has the CLI wrapper for creating an admin etc.
    mainClass.set("de.rptu.cs.exclaim.Main")
}

tasks.withType<BootRun> {
    environment["spring.output.ansi.console-available"] = true // Enable colors in console log
    doFirst { standardInput = System.`in` }  // for the CLI tool, must be in doFirst due to configuration caching
}

tasks.withType<BootJar> {
    // Add app name to manifest
    manifest.attributes(
        mapOf(
            "Implementation-Title" to "ExClaim"
        )
    )
}

repositories {
    maven {
        name = "Shibboleth"
        url = uri("https://build.shibboleth.net/maven/releases/")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.security:spring-security-messaging")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // API for types shared with the frontend
    implementation(project(":api"))

    // Flyway Migrations
    implementation("org.flywaydb:flyway-core")
    runtimeOnly(project(":flyway"))

    // Generated classes from jOOQ code generator
    implementation(project(":jooq"))

    // Further application dependencies
    implementation("info.picocli:picocli:4.7.5") // CLI Parser
    implementation("com.ibm.icu:icu4j:70.1") // ICU Internationalization
    implementation("org.jooq:jool:0.9.14") // Tuples
    implementation("com.google.guava:guava:31.1-jre") // Suppliers.memoize

    implementation("com.vladsch.flexmark:flexmark-all:0.27.0") // Markdown
    implementation("com.opencsv:opencsv:4.0") // CSV Parser
    implementation("io.prometheus:simpleclient_servlet_jakarta:0.16.0") // Monitoring
    implementation("org.jasypt:jasypt:1.9.3") // RFC2307SMD5PasswordEncryptor

    // webjars
    runtimeOnly("org.webjars.npm:bootstrap:3.3.0")
    runtimeOnly("org.webjars.npm:jquery:2.1.1")
    runtimeOnly("org.webjars.npm:monaco-editor:0.10.1")

    // Testing
    endToEndTestImplementation("org.springframework:spring-test")
    endToEndTestImplementation("org.springframework.boot:spring-boot-test")
    endToEndTestImplementation("org.hamcrest:hamcrest")
    endToEndTestImplementation("com.icegreen:greenmail:1.6.5")
}

// Error Prone: Add our custom bug patterns. Disable NullAway for picocli.
dependencies {
    errorprone(project(":errorprone"))
}
SqlDialect.VALID_DIALECTS.forEach { dialect ->
    sourceSets.named(dialect.sourceSetName) {
        extensions.getByType<ErrorProneExtension>().options.add(
            "-XepOpt:NullAway:ExcludedFieldAnnotations=picocli.CommandLine.Option,picocli.CommandLine.ParentCommand"
        )
    }
}

// Configuration for IntelliJ IDEA
idea {
    module {
        excludeDirs.add(file("data/"))
    }
}

// Add generated frontend code to resources folder
SqlDialect.VALID_DIALECTS.forEach { dialect ->
    tasks.named<ProcessResources>(sourceSets[dialect.sourceSetName].processResourcesTaskName) {
        val frontendTask = tasks.findByPath(":frontend:npmRunBuild")!!
        val metadataFiles: Set<String> = setOf("frontend-manifest.json", "frontend-routes.regexp");
        from(frontendTask) {
            exclude(metadataFiles)
            into("static")
        }
        from(frontendTask) {
            include(metadataFiles)
        }

        // Also copy docs
        from(tasks.findByPath(":docs:npmRunBuild")!!) {
            into("static/docs")
        }

        // Also copy proof tree generator
        from(tasks.findByPath(":proof-tree-generator:npmRunBuild")!!) {
            into("static/proof-tree-generator")
        }

        // Also copy recursion tutor
        from(tasks.findByPath(":recursion-tutor:npmRunBuild")!!) {
            into("static/recursion-tutor")
        }
    }
}

// Copy the generated spring-configuration-metadata.json file from h2 to main such that IntelliJ IDEA finds it.
val copySpringConfigurationMetadata = tasks.register<Copy>("copySpringConfigurationMetadata") {
    description = "Copies the generated spring-configuration-metadata.json file from h2 to main."
    dependsOn(tasks.compileH2Java)
    from(tasks.compileH2Java.get().destinationDirectory.file("META-INF/spring-configuration-metadata.json"))
    into(tasks.compileJava.get().destinationDirectory.dir("META-INF"))
}
tasks.h2Classes { dependsOn(copySpringConfigurationMetadata) }
