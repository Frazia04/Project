/**
 * Root Project
 *
 * This file does not configure the build. Please refer to the build.gradle.kts files in the subprojects.
 */

import de.rptu.cs.exclaim.gradle.SqlDialect
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

buildscript {
    dependencies {
        classpath("org.yaml:snakeyaml:${project.extra["snakeyaml_version"]}")
    }
}
plugins {
    base
    id("org.jetbrains.gradle.plugin.idea-ext")
}

// Run configuration for IntelliJ IDEA
idea.project.settings.runConfigurations {
    SqlDialect.VALID_DIALECTS.forEach { dialect ->
        register<Gradle>("bootJar (${dialect.name})") {
            setProject(rootProject.project("app"))
            taskNames = listOf("bootJar${dialect.name}")
        }
        register<Gradle>("bootRun (${dialect.name})") {
            setProject(rootProject.project("app"))
            taskNames = listOf("bootRun${dialect.name}")
        }
    }
    register<Gradle>("bootJar") {
        setProject(rootProject.project("app"))
        taskNames = SqlDialect.VALID_DIALECTS.map { dialect -> "bootJar${dialect.name}" }
    }
    register<Gradle>("check") {
        taskNames = listOf("check")
    }
    register<Gradle>("build") {
        taskNames = listOf("build")
    }
    register<Gradle>("clean") {
        taskNames = listOf("clean")
    }
    register<Gradle>("generateJooq (H2)") {
        setProject(rootProject.project("jooq"))
        taskNames = listOf("generateJooqH2")
    }
}

// Keep the .gitlab-ci.yml file in sync with our Gradle wrapper version
tasks.wrapper {
    val injected = project.objects.newInstance<Injected>()
    doLast {
        logger.info("Patching .gitlab-ci.yml file to use the correct Docker image...")
        try {
            val dockerVersion = if (gradleVersion.chars().filter { c -> c.toChar() == '.' }.count() >= 2)
                gradleVersion else "$gradleVersion.0"
            injected.execOperations.exec {
                commandLine(
                    "sed",
                    "-i",
                    "-E",
                    "s/^(\\s*image: gradle:)[0-9][0-9a-zA-Z.-]*(-jdk[0-9.]+)$/\\1$dockerVersion\\2/",
                    ".gitlab-ci.yml"
                )
            }
        } catch (e: Exception) {
            logger.error("Could not patch the .gitlab-ci.yml file: {}", e.message)
            throw e
        }
    }
}
interface Injected {
    @get:Inject
    val execOperations: ExecOperations
}

// Make sure that Docker tags in .gitlab-ci.yml and gradle.properties are consistent
val verifyGitLabCIServicesDockerTags = tasks.register("verifyGitLabCIServicesDockerTags") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check that Docker tags in .gitlab-ci.yml services are consistent with entries in gradle.properties."
    val gitlabFile = project.file(".gitlab-ci.yml")
    val gradleProperties = project.ext
    doLast {
        // Map from Docker image name to tag as defined in the .gitlab-ci.yml file
        logger.info("Parsing .gitlab-ci.yml to extract Docker tags of service images...")
        val gitlabVersions = org.yaml.snakeyaml.Yaml().load<Map<String, Map<String, List<String>>>>(
            gitlabFile.inputStream()
        )?.get("build")?.get("services")?.associate { val (image, tag) = it.split(':'); Pair(image, tag) }
            ?: throw Exception("Could not parse .gitlab-ci.yml file")
        logger.info("Parsed Docker tags from .gitlab-ci.yml: {}", gitlabVersions)

        // Map from Docker image name to the name of the property in gradle.properties holding the Docker tag
        val gradlePropertyNames = de.rptu.cs.exclaim.gradle.DatabaseServerProvider.DATABASES.entries.associateBy(
            { it.value.image }, { it.key.name.lowercase() + "_docker_tag" }
        ).plus(de.rptu.cs.exclaim.gradle.EndToEndTestsPlugin.BROWSERS.associateBy(
            { it.image }, { "selenium_" + it.name.lowercase() + "_docker_tag" }
        ))

        val mismatches = mutableListOf<String>()
        gradlePropertyNames.forEach { (image, propertyName) ->
            val gradleVersion = gradleProperties[propertyName] as String
            val gitlabVersion = gitlabVersions[image]
            if (gitlabVersion == gradleVersion) {
                logger.info(
                    "Docker tag of GitLab CI service '{}' defined in .gitlab-ci.yml matches '{}' property defined in gradle.properties: {}",
                    image,
                    propertyName,
                    gradleVersion
                )
            } else {
                mismatches.add(
                    "  - gradle.properties defines $propertyName=$gradleVersion\n    .gitlab-ci.yml " +
                            if (gitlabVersion != null) "defines service $image:$gitlabVersion"
                            else "does not define a service for Docker image $image"
                )
            }
        }
        if (mismatches.isNotEmpty()) {
            throw Exception(
                mismatches.joinToString(
                    prefix = "Services defined in .gitlab-ci.yml are not consistent with the Docker tags specified in gradle.properties!\n",
                    separator = "\n"
                )
            )
        }
    }
}
tasks.check { dependsOn(verifyGitLabCIServicesDockerTags) }
