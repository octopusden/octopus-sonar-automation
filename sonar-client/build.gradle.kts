plugins {
    kotlin("jvm")
}

group = "org.octopusden.octopus.sonar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${properties["jackson.version"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${properties["jackson.version"]}")
    implementation("io.github.openfeign:feign-core:13.5")
    implementation("io.github.openfeign:feign-jackson:13.5")
    implementation("io.github.openfeign:feign-slf4j:13.5")
    implementation("io.github.openfeign:feign-httpclient:13.5")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}