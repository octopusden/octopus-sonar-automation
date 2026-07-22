pluginManagement {
    plugins {
        kotlin("jvm") version (extra["kotlin.version"] as String)
        id("com.gradleup.shadow") version (extra["shadow.version"] as String)
        id("io.github.gradle-nexus.publish-plugin") version (extra["nexus-publish-plugin.version"] as String)
        id("io.gitlab.arturbosch.detekt") version (extra["detekt.version"] as String)
        id("org.jlleitschuh.gradle.ktlint") version (extra["ktlint.version"] as String)
        id("org.octopusden.octopus-quality") version (extra["octopus-quality.version"] as String)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "sonar-automation"

include("sonar-client")
