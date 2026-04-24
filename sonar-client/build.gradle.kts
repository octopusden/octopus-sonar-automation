plugins {
    kotlin("jvm")
}

group = "org.octopusden.octopus.sonar"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${properties["jackson.version"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${properties["jackson.version"]}")
    implementation("io.github.openfeign:feign-core:${properties["feign.version"]}")
    implementation("io.github.openfeign:feign-jackson:${properties["feign.version"]}")
    implementation("io.github.openfeign:feign-slf4j:${properties["feign.version"]}")
    implementation("io.github.openfeign:feign-httpclient:${properties["feign.version"]}")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}