import java.time.Duration

plugins {
    kotlin("jvm")
    `maven-publish`
    id("com.gradleup.shadow")
    id("io.github.gradle-nexus.publish-plugin")
    signing
}

group = "org.octopusden.octopus.sonar"
description = "Octopus SonarQube Automation"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sonar-client"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${properties["jackson.version"]}")
    implementation("org.octopusden.octopus.infrastructure:components-registry-service-client:${properties["components-registry-client.version"]}")
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
            "tokens" to mapOf(
                "name" to project.name,
                "version" to project.version.toString(),
                "group" to project.group.toString()
            ),
            "beginToken" to "\${",
            "endToken" to "}"
        )
    }
}

configurations {
    create("distributions")
}

val metarunners = artifacts.add(
    "distributions",
    layout.buildDirectory.file("distributions/metarunners.zip").get().asFile
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
    isRequired = System.getenv().containsKey("ORG_GRADLE_PROJECT_signingKey") && System.getenv().containsKey("ORG_GRADLE_PROJECT_signingPassword")
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
