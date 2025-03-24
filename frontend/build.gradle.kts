/**
 * Subproject: Frontend
 *
 * In order to compile the TypeScript sources, a local Node.js environment is automatically set up during the Gradle
 * build such that backend developers do not need to install it on their machine.
 */

import de.rptu.cs.exclaim.gradle.NodeJsPlugin.FRONTEND_GROUP
import de.rptu.cs.exclaim.gradle.NodeJsPlugin.addCheckTask
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

plugins {
    id("exclaim.node-js")
}

evaluationDependsOn(":api")

// The task :api:generateTypescriptApi generates type definitions into the build directory of the api subproject.
// We copy the generated file into src/api such that frontend code can find it. We add a comment/warning that it is
// auto-generated and must not be edited manually.
val generateTypescriptApi = tasks.register("generateTypescriptApi") {
    group = FRONTEND_GROUP
    description = "Generates the TypeScript API interfaces."
    val inputFiles = project.files(tasks.findByPath(":api:generateTypescriptApi"))
    inputs.files(inputFiles)
    val outputFile = project.layout.projectDirectory.file("src/api/types.ts")
    outputs.file(outputFile)
    outputs.cacheIf { true }
    doLast {
        outputFile.asFile.outputStream().use { output ->
            val writer = PrintWriter(output, false, StandardCharsets.UTF_8)
            writer.println("// THIS FILE IS AUTO-GENERATED. DO NOT EDIT MANUALLY! CHANGES WILL BE LOST!")
            writer.println()
            writer.println("// These type definitions are generated from Java types in the api subproject.")
            writer.println("// Source: ../../../api/src/main/java/de/rptu/cs/exclaim/api")
            writer.println("// To rebuild this file, run Gradle task :frontend:generateTypescriptApi")
            writer.println()
            writer.flush()
            inputFiles.files.forEach { inputFile ->
                inputFile.inputStream().use { it.copyTo(output) }
            }
        }
    }
}

tasks.npmRunBuild {
    inputs.files(generateTypescriptApi)
}

addCheckTask(project).configure {
    inputs.files(generateTypescriptApi)
}
