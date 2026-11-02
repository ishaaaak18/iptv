// Standalone build: pure Kotlin/JVM logic with no Android dependency.
// It is included in the root build via includeBuild("core"), and can also be
// built and tested on its own:  gradle -p core test
rootProject.name = "core"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
