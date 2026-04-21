package org.octopusden.octopus.sonar.resolver.parameters

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
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
     * - Component uses Java or Kotlin **and** either its `javaVersion` build parameter is `17` or `21`,
     *   or it is listed in `mismatch-java-version.txt` - those components are handled by
     *   the Gradle/Maven Sonar plugins
     */
    fun skipSonarMetarunnerExecution(componentName: String, componentVersion: String): Boolean {
        if (componentName in appliedSastComponents) {
            logger.info("$componentName is in applied-sast.json - skipping")
            return true
        }

        if (componentName in otherDocComponents || componentName.isDocPrefix()) {
            logger.info("$componentName is a documentation component - skipping")
            return true
        }

        val component = crsClient.getDetailedComponent(componentName, componentVersion)

        if (component.archived) {
            logger.info("$componentName is archived - skipping")
            return true
        }

        if (component.labels.contains("test-component")) {
            logger.info("$componentName is labelled test-component - skipping")
            return true
        }

        val isJavaOrKotlin = component.labels.contains("java") || component.labels.contains("kotlin")
        val isModernJava = component.buildParameters?.javaVersion == "17" || component.buildParameters?.javaVersion == "21"
        val isMismatchComponent = componentName in mismatchJavaVersionComponents

        if (isJavaOrKotlin && (isModernJava || isMismatchComponent)) {
            logger.info("$componentName uses java/kotlin - skipping (handled by Gradle/Maven plugins)")
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
        if (componentName in otherDocComponents || componentName.isDocPrefix()) {
            logger.info("$componentName is a documentation component - skipping")
            return true
        }

        val component = crsClient.getById(componentName)

        if (component.archived) {
            logger.info("$componentName is archived - skipping")
            return true
        }

        if (component.labels.contains("test-component")) {
            logger.info("$componentName is labelled test-component - skipping")
            return true
        }

        return false
    }

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

