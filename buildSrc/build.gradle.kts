/**
 * Project: buildSrc
 *
 * This project is an included build. It compiles custom Gradle plugins that we use in the other build.gradle.kts files.
 * Each plugin consists of a class in src/main/java/ and an entry below in the gradlePlugin block in this file.
 *
 * This build.gradle.kts file defines how our plugins are built. We add Lombok and Error Prone with NullAway, but of
 * course cannot yet use our custom plugins.
 */

import java.io.FileInputStream
import java.util.Properties

plugins {
    `java-gradle-plugin`
    idea
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

gradlePlugin {
    plugins {
        for (name in listOf(
            "base",
            "end-to-end-tests",
            "error-prone",
            "jacoco-aggregate-report",
            "junit",
            "junit-integration-tests",
            "lombok",
            "node-js",
            "platform",
            "sql-dialect",
            "sql-dialect-outgoing-variants",
            "sql-dialect-source-sets",
            "sql-dialect-source-sets-depend-main",
            "sql-dialect-source-sets-include-main",
            "sql-dialect-spring-boot",
            "sql-dialect-test-source-sets",
            "sql-dialect-test-source-sets-depend-test",
            "sql-dialect-test-source-sets-include-test",
        )) {
            create(name) {
                val camelCaseName = name
                    .split('-')
                    .joinToString(separator = "", transform = { it.replaceFirstChar { it.uppercase() } })
                    .replace(Regex("^Junit"), "JUnit")
                id = "exclaim.$name"
                implementationClass = "de.rptu.cs.exclaim.gradle.${camelCaseName}Plugin"
            }
        }
    }
}

// Load the gradle.properties from the root folder to get our version properties.
val props = Properties()
props.load(FileInputStream(file("../gradle.properties")))

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:" + props["spring_boot_version"])
    implementation("com.github.node-gradle:gradle-node-plugin:" + props["gradle_node_plugin_version"])
    implementation("org.testcontainers:postgresql:" + props["testcontainers_version"])
    implementation("org.projectlombok:lombok:" + props["lombok_version"])
    annotationProcessor("org.projectlombok:lombok:" + props["lombok_version"])
    annotationProcessor("com.google.errorprone:error_prone_core:" + props["errorprone_version"])
    annotationProcessor("com.uber.nullaway:nullaway:" + props["nullaway_version"])
}

// Configure the JavaCompile task. See https://errorprone.info/docs/installation#command-line
tasks.withType<JavaCompile> {
    options.isFork = true
    options.forkOptions.jvmArgs!!.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        )
    )
    options.compilerArgs.addAll(
        listOf(
            "-XDcompilePolicy=simple",
            "-Xplugin:ErrorProne -XepDisableWarningsInGeneratedCode -XepOpt:NullAway:AnnotatedPackages=de.rptu.cs.exclaim -XepOpt:NullAway:AcknowledgeRestrictiveAnnotations=true",
            "-Xlint:all,-processing,-serial",
        )
    )
}

// IntelliJ IDEA: Download Javadoc and sources for better support
idea.module {
    isDownloadJavadoc = true
    isDownloadSources = true
}
