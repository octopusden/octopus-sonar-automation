import java.time.Duration

plugins {
    kotlin("jvm")
    `maven-publish`
    id("com.gradleup.shadow")
    id("io.github.gradle-nexus.publish-plugin")
    signing
    // Kotlin static-analysis tools — applied to this (root) Kotlin module; also applied
    // in the sonar-client subproject. Versions come from settings.gradle.kts pluginManagement.
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    // Octopus quality-gates convention plugin — configures detekt/ktlint and wires qualityStatic.
    id("org.octopusden.octopus-quality")
}

group = "org.octopusden.octopus.sonar"
description = "Octopus SonarQube Automation"

octopusQuality {
    // Regression guard on what this repository publishes to Maven Central. It had none before —
    // pinned below v2.6.0, so neither this check nor the release-time size guard applied.
    publication {
        enforceCentralPublications.set(true)
        centralPublications.set(
            setOf(
                // No `jar:all` here despite the shadowJar task existing — the shadow
                // artifact is built but never added to the publication, confirmed against
                // Central. So no fat-jar-publication-allowlist entry is needed either.
                ":|maven|org.octopusden.octopus.sonar:sonar-automation|" +
                    "[jar, jar:javadoc, jar:sources, zip:metarunners]",
            ),
        )
    }
    // Repo has no coverage tool / no coverage target — disable coverage verification.
    coverage {
        enabled.set(false)
    }
    // Enforce the gate: detekt/ktlint violations fail the build. Current debt is absorbed
    // by the committed detekt-baseline.xml / ktlint-baseline.xml files.
    kotlin {
        failOnViolation.set(true)
    }
}

// NOTE: octopus-quality 2.4.0 gates the ROOT module automatically when it carries its own
// Kotlin sources (src/main + src/test), configuring detekt/ktlint (shared detekt.yml +
// .editorconfig, committed baselines, failure enforcement) and folding both tasks into
// qualityStatic. The previously-required manual detekt/ktlint config + qualityStatic wiring
// (a 2.3.5 gap workaround) is therefore redundant and has been removed. The detekt/ktlint
// plugins must still be applied here (see plugins {}) so the plugin can configure them and
// the hollow-gate guard is satisfied.

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sonar-client"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${properties["jackson.version"]}")
    implementation(
        "org.octopusden.octopus.infrastructure:components-registry-service-client:${properties["components-registry-client.version"]}",
    )
    implementation("org.octopusden.octopus.vcsfacade:client:${properties["vcs-facade-client.version"]}")
    implementation("com.github.ajalt.clikt:clikt:${properties["clikt.version"]}")
    implementation("ch.qos.logback:logback-classic:${properties["logback.version"]}")
    implementation(platform("io.github.openfeign:feign-bom:${properties["feign.version"]}"))
    implementation("org.apache.velocity:velocity-engine-core:${properties["velocity-core.version"]}")
    implementation("org.apache.velocity.tools:velocity-tools-generic:${properties["velocity-tools.version"]}") {
        exclude(group = "org.slf4j")
    }

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:${properties["mockk.version"]}")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier = ""
    manifest {
        attributes["Main-Class"] = "org.octopusden.octopus.sonar.MainKt"
    }
}

tasks.jar {
    enabled = false
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.register<Zip>("zipMetarunners") {
    archiveFileName = "metarunners.zip"
    from(layout.projectDirectory.dir("metarunners")) {
        filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to
                mapOf(
                    "name" to project.name,
                    "version" to project.version.toString(),
                    "group" to project.group.toString(),
                ),
            "beginToken" to "\${",
            "endToken" to "}",
        )
    }
}

configurations {
    create("distributions")
}

val metarunners =
    artifacts.add(
        "distributions",
        layout.buildDirectory
            .file("distributions/metarunners.zip")
            .get()
            .asFile,
    ) {
        classifier = "metarunners"
        type = "zip"
        builtBy("zipMetarunners")
    }

tasks.named("build") {
    dependsOn(tasks.named("zipMetarunners"))
    dependsOn(tasks.shadowJar)
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            artifact(metarunners)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/octopusden/${project.name}.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/octopusden/${project.name}.git")
                    connection.set("scm:git://github.com/octopusden/${project.name}.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
        }
    }
}

signing {
    isRequired =
        System.getenv().containsKey("ORG_GRADLE_PROJECT_signingKey") &&
        System.getenv().containsKey("ORG_GRADLE_PROJECT_signingPassword")
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
