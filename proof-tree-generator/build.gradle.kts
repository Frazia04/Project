/**
 * Subproject: Proof Tree Generator
 *
 * In order to compile the TypeScript sources, a local Node.js environment is automatically set up during the Gradle
 * build such that backend developers do not need to install it on their machine.
 */

import de.rptu.cs.exclaim.gradle.NodeJsPlugin.FRONTEND_GROUP
import de.rptu.cs.exclaim.gradle.NodeJsPlugin.addCheckTask

plugins {
    id("exclaim.node-js")
}
