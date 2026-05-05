package org.octopusden.octopus.sonar.resolver.parameters

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.components.registry.core.dto.DetailedComponent
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sonar project override for applied-SAST and other pre-configured components.
 */
data class SonarProjectOverride(
    val sonarProjectKey: String,
    val sonarProjectName: String,
)

/**
 * Determines whether Sonar execution steps should be skipped for a given component.
 */
class SonarExecutionResolver(
    private val crsClient: ComponentsRegistryServiceClient,
    configDir: Path,
) {

    private val appliedSastComponents: Map<String, SonarProjectOverride> = loadAppliedSast(configDir)
    private val otherDocComponents: Set<String> = loadList(configDir, OTHER_DOC_FILE)
    private val mismatchJavaVersionComponents: Set<String> = loadList(configDir, MISMATCH_JAVA_VERSION_FILE)

    /**
     * Returns the Sonar project override if the component is in the applied-SAST list, or null otherwise.
     */
    fun getAppliedSastOverride(componentName: String): SonarProjectOverride? = appliedSastComponents[componentName]

    /**
     * Returns `true` when the Sonar metarunner should be **skipped** for this component.
     *
     * Sonar execution is skipped when any of the following conditions hold:
     * - Component is listed in `applied-sast.json` - already covered by a dedicated SAST pipeline
     * - Component name starts with `doc-` or `doc_` (case-insensitive) or is listed in `other-doc-components.txt`
     * - Component is archived
     * - Component is labelled `test-component`
     * - Component uses Java or Kotlin **and** uses a Gradle or Maven build system **and** either
     *   its `javaVersion` build parameter is `17` or `21`, or it is listed in
     *   `mismatch-java-version.txt` - those components are handled by the Gradle/Maven Sonar plugins
     */
    fun skipSonarMetarunnerExecution(componentName: String, componentVersion: String): Boolean {
        skipIfAppliedSast(componentName)?.let { return it }
        skipIfDoc(componentName)?.let { return it }

        val component = crsClient.getDetailedComponent(componentName, componentVersion)

        skipIfArchivedOrTest(componentName, component.archived, component.labels)?.let { return it }

        val isJavaOrKotlin = component.isJavaOrKotlin()
        val isModernOrMismatch = component.isModernJava() || componentName in mismatchJavaVersionComponents
        val isPluginEligibleBuildSystem =
            component.buildSystem == BuildSystem.GRADLE || component.buildSystem == BuildSystem.MAVEN

        if (isPluginEligibleBuildSystem && isJavaOrKotlin && isModernOrMismatch) {
            logger.info("$componentName uses java/kotlin with ${component.buildSystem} - skipping (handled by Gradle/Maven plugins)")
            return true
        }

        return false
    }

    /**
     * Returns `true` when Sonar report generation should be **skipped** for this component.
     *
     * Report generation is skipped when any of the following conditions hold:
     * - Component name starts with `doc-` or `doc_` (case-insensitive) or is listed in `other-doc-components.txt`
     * - Component is archived
     * - Component is labelled `test-component`
     */
    fun skipSonarReportGeneration(componentName: String): Boolean {
        skipIfDoc(componentName)?.let { return it }

        val component = crsClient.getById(componentName)

        skipIfArchivedOrTest(componentName, component.archived, component.labels)?.let { return it }

        return false
    }

    /**
     * Determines whether the Sonar build-tool plugin (Gradle or Maven) should run.
     *
     * Returns the [BuildSystem] (`GRADLE` or `MAVEN`) when the plugin **should** run,
     * or `null` when execution should be **skipped**.
     *
     * The plugin runs only when **all** of the following hold:
     * - Component is not in `applied-sast.json`
     * - Component is not a documentation component
     * - Component is not archived or labelled `test-component`
     * - Component uses the **Gradle** or **Maven** build system
     * - Component is labelled `java` or `kotlin`
     * - Component uses Java 17/21 or is listed in `mismatch-java-version.txt`
     */
    fun resolveSonarPluginBuildSystem(componentName: String, componentVersion: String): BuildSystem? {
        skipIfAppliedSast(componentName)?.let { return null }
        skipIfDoc(componentName)?.let { return null }

        val component = crsClient.getDetailedComponent(componentName, componentVersion)

        skipIfArchivedOrTest(componentName, component.archived, component.labels)?.let { return null }

        val buildSystem = component.buildSystem
        if (buildSystem != BuildSystem.GRADLE && buildSystem != BuildSystem.MAVEN) {
            logger.info("$componentName uses $buildSystem - skipping Sonar plugin execution")
            return null
        }

        if (!component.isJavaOrKotlin()) {
            logger.info("$componentName is not java/kotlin - skipping Sonar plugin execution")
            return null
        }

        if (component.isModernJava() || componentName in mismatchJavaVersionComponents) {
            return buildSystem
        }

        return null
    }

    private fun skipIfAppliedSast(componentName: String): Boolean? {
        if (componentName in appliedSastComponents) {
            logger.info("$componentName is in applied-sast.json - skipping")
            return true
        }
        return null
    }

    private fun skipIfDoc(componentName: String): Boolean? {
        if (componentName in otherDocComponents || componentName.isDocPrefix()) {
            logger.info("$componentName is a documentation component - skipping")
            return true
        }
        return null
    }

    private fun skipIfArchivedOrTest(componentName: String, archived: Boolean, labels: Set<String>): Boolean? {
        if (archived) {
            logger.info("$componentName is archived - skipping")
            return true
        }
        if (labels.contains("test-component")) {
            logger.info("$componentName is labelled test-component - skipping")
            return true
        }
        return null
    }

    private fun DetailedComponent.isJavaOrKotlin(): Boolean =
        labels.contains("java") || labels.contains("kotlin")

    private fun DetailedComponent.isModernJava(): Boolean =
        buildParameters?.javaVersion == "17" || buildParameters?.javaVersion == "21"

    companion object {
        private val logger = LoggerFactory.getLogger(SonarExecutionResolver::class.java)
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        private const val APPLIED_SAST_FILE = "applied-sast.json"
        private const val OTHER_DOC_FILE = "other-doc-components.txt"
        private const val MISMATCH_JAVA_VERSION_FILE = "mismatch-java-version.txt"

        private fun loadAppliedSast(configDir: Path): Map<String, SonarProjectOverride> {
            val externalFile = configDir.resolve(APPLIED_SAST_FILE)
            if (!Files.exists(externalFile)) {
                error("Could not load Sonar applied-SAST config: $externalFile")
            }

            logger.info("Loading Sonar applied-SAST config from: $externalFile")
            val content = Files.readString(externalFile)
            return objectMapper.readValue(content, object : TypeReference<Map<String, SonarProjectOverride>>() {})
        }

        private fun loadList(configDir: Path, fileName: String): Set<String> {
            val externalFile = configDir.resolve(fileName)
            if (!Files.exists(externalFile)) {
                error("Could not load Sonar skip list: $externalFile")
            }

            logger.info("Loading Sonar skip list from external config: $externalFile")
            return Files.readAllLines(externalFile)
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { !it.startsWith("#") }
                .toSet()
        }
    }

}

private fun String.isDocPrefix(): Boolean =
    startsWith("doc-", ignoreCase = true) || startsWith("doc_", ignoreCase = true)
            || endsWith("-doc", ignoreCase = true) || endsWith("_doc", ignoreCase = true)

