pluginManagement {
    plugins {
        kotlin("jvm") version (extra["kotlin.version"] as String)
        id("com.gradleup.shadow") version (extra["shadow.version"] as String)
        id("io.github.gradle-nexus.publish-plugin") version (extra["nexus-publish-plugin.version"] as String)
        id("com.jfrog.artifactory") version (extra["jfrog-artifactory.version"] as String)
    }
}

rootProject.name = "sonar-automation"

include("sonar-client")
