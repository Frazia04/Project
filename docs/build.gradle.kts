/**
 * Subproject: Docs
 *
 * We use <a href="https://antora.org/">Antora</a> for documentation, which requires Node.js. Like for the frontend
 * subproject, we set up a local Node.js environment automatically during the Gradle build.
 */

import com.github.gradle.node.npm.task.NpmTask
import de.rptu.cs.exclaim.gradle.NodeJsPlugin
import de.rptu.cs.exclaim.gradle.NodeJsPlugin.FRONTEND_GROUP
import de.rptu.cs.exclaim.gradle.NodeJsPlugin.addCheckTask
import de.rptu.cs.exclaim.gradle.Utils.detectLogLevel

plugins {
    id("exclaim.node-js")
}

// Gradle task for build:ui
val uiOutputDir = "ui/dist"
extensions.getByType<NodeJsPlugin.NodeJsPluginExtension>().excludeFromInputs.add(uiOutputDir)
val npmRunBuildUi = tasks.register<NpmTask>("npmRunBuildUi") {
    group = FRONTEND_GROUP
    description = "Builds the Antora UI bundle."
    npmCommand = listOf("run", "build:ui")
    outputs.dir(uiOutputDir)
    outputs.cacheIf { true }
}

// Gradle task for build:docs
tasks.npmRunBuild {
    npmCommand = listOf("run", "build:docs").plus(
        if (detectLogLevel(logger) >= LogLevel.LIFECYCLE) listOf("--", "--quiet")
        else listOf()
    )
    inputs.files(npmRunBuildUi)
}

// Also clean output of build:ui
tasks.clean {
    delete(npmRunBuildUi)
}

addCheckTask(project)
