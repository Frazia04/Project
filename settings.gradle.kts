rootProject.name = "exclaim"
include(
    "api",
    "app",
    "docs",
    "emp",
    "errorprone",
    "flyway",
    "frontend",
    "jooq",
    "proof-tree-generator",
    "recursion-tutor",
)

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    plugins {
        id("org.jetbrains.gradle.plugin.idea-ext") version "${extra["idea_ext_version"]}"
    }
}
