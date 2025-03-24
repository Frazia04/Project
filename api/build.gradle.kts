/**
 * Subproject: API
 *
 * This subproject contains types that are shared between frontend and backend. The types are defined as Java types in
 * source set main. There is a task "generateTypescriptApi" that generates TypeScript interfaces from our Java code.
 *
 * We use the following tool for generating TypeScript from Java: https://github.com/vojtechhabarta/typescript-generator
 * The author's Gradle plugin has some issues, e.g. affecting Gradle's configuration cache. We therefore have our own
 * Gradle task "generateTypescriptApi" that runs the Main class from the "typescriptGenerator" source set.
 */

import de.rptu.cs.exclaim.gradle.JavaExecOutputLogger
import de.rptu.cs.exclaim.gradle.Utils

plugins {
    id("exclaim.base")
    id("exclaim.lombok")
    id("exclaim.error-prone")
}

// Define an additional source set to compile the typescript-generator configuration.
sourceSets.register("typescriptGenerator")

dependencies {
    // For compiling the API classes
    implementation("org.springframework:spring-core") // @Nullable annotation
    implementation("com.fasterxml.jackson.core:jackson-annotations") // @JsonFormat annotation

    // For compiling the typescript-generator configuration and executing the generator
    "typescriptGeneratorImplementation"("cz.habarta.typescript-generator:typescript-generator-core")
    "typescriptGeneratorImplementation"("org.springframework:spring-core")
}

tasks.register<JavaExec>("generateTypescriptApi") {
    group = "frontend"
    description = "Generates the TypeScript API interfaces."
    classpath = sourceSets["typescriptGenerator"].runtimeClasspath + sourceSets["main"].output
    mainClass.set("de.rptu.cs.exclaim.typescriptGenerator.Main")
    JavaExecOutputLogger.provideLogLevel(this)
    val outputFile = layout.buildDirectory.get().file("generated-src/main/typescript/api.ts")
    systemProperties["exclaim.output_file"] = outputFile.asFile.path
    outputs.file(outputFile)
    outputs.cacheIf { true }
    Utils.doFirstDelete(this, outputFile) // cleanup from previous run
}
