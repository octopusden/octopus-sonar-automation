package org.octopusden.octopus.sonar.resolver

import org.octopusden.octopus.sonar.resolver.parameters.SonarExecutionResolver
import org.octopusden.octopus.sonar.test.Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SonarExecutionResolverTest {

    private lateinit var crsClient: ComponentsRegistryServiceClient
    private lateinit var resolver: SonarExecutionResolver

    @BeforeTest
    fun setUp() {
        crsClient = mockk()
        val configDir =
            SonarExecutionResolverTest::class.java.classLoader
                .getResource("sonar-config")
                ?.toURI()
                ?.let { java.nio.file.Path.of(it) }
                ?: error("Missing test resource directory: sonar-config")
        resolver = SonarExecutionResolver(crsClient, configDir)
    }

    // ════════════════════════════════════════════════════════════════════════
    // skipSonarMetarunnerExecution
    // ════════════════════════════════════════════════════════════════════════

    // ── applied-sast list ─────────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for component in applied-sast list`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0"))
    }

    @Test
    fun `metarunner skip for applied-sast component does not call CRS`() {
        resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0")
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `metarunner skipped for component in applied-sast list (JSON-based config)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0"))
    }

    @Test
    fun `metarunner skip for applied-sast component (JSON-based config) does not call CRS`() {
        resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0")
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `getAppliedSastOverride returns override for applied-sast component`() {
        val override = resolver.getAppliedSastOverride("component-with-sast")
        assertNotNull(override)
        assertEquals("PROJECT_component-with-sast", override.sonarProjectKey)
        assertEquals("PROJECT/component-with-sast", override.sonarProjectName)
    }

    @Test
    fun `getAppliedSastOverride returns null for non-applied-sast component`() {
        every { crsClient.getDetailedComponent("regular-comp", "1.0") } returns Fixtures.detailedComponent()
        val override = resolver.getAppliedSastOverride("regular-comp")
        assertNull(override)
    }

    // ── documentation components ───────────────────────────────────────────────

    @Test
    fun `metarunner skipped for component name starting with doc-`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("doc-component", "1.0"))
    }

    @Test
    fun `metarunner skipped for component name starting with doc_`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("doc_component", "1.0"))
    }

    @Test
    fun `metarunner skipped for component name starting with DOC_ (case-insensitive)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("DOC_format", "1.0"))
    }

    @Test
    fun `metarunner skipped for component name ending with -doc`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-doc", "1.0"))
    }

    @Test
    fun `metarunner skipped for component name ending with _doc`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component_doc", "1.0"))
    }

    @Test
    fun `metarunner skipped for component name ending with -DOC (case-insensitive)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-DOC", "1.0"))
    }

    @Test
    fun `metarunner not skipped for component name starting with doc but not doc- prefix`() {
        every { crsClient.getDetailedComponent("docker-registry", "1.0") } returns Fixtures.detailedComponent()
        assertFalse(resolver.skipSonarMetarunnerExecution("docker-registry", "1.0"))
    }

    @Test
    fun `metarunner skipped for component in other-doc-components list`() {
        // "other-doc-component" is in other-doc-components.txt
        assertTrue(resolver.skipSonarMetarunnerExecution("other-doc-component", "1.0"))
    }

    // ── archived ──────────────────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for archived component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(archived = true)
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    // ── test-component label ──────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for component labelled test-component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("test-component"))
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    // ── java/kotlin with modern JDK ───────────────────────────────────────────

    @Test
    fun `metarunner skipped for java component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "17")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner skipped for java component with javaVersion 21`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "21")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner skipped for kotlin component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("kotlin"), javaVersion = "17")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner skipped for java component in mismatch-java-version list`() {
        // "mismatch-java-component" is the first entry in mismatch-java-version.txt
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "21")
        assertTrue(resolver.skipSonarMetarunnerExecution("mismatch-java-component", "1.0"))
    }

    // ── should NOT skip ───────────────────────────────────────────────────────

    @Test
    fun `metarunner not skipped for regular active component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent()
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner not skipped for java component on old javaVersion not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "8")
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner not skipped for java component with no javaVersion and not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"))
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    @Test
    fun `metarunner skipped for java component with old javaVersion but in mismatch list`() {
        // mismatch-java-component is in mismatch-java-version.txt, meaning it actually uses modern JDK
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "8")
        assertTrue(resolver.skipSonarMetarunnerExecution("mismatch-java-component", "1.0"))
    }

    @Test
    fun `metarunner not skipped for non-java non-kotlin component even with modern javaVersion`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("python"), javaVersion = "17")
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // skipSonarReportGeneration
    // ════════════════════════════════════════════════════════════════════════

    // ── documentation components ───────────────────────────────────────────────

    @Test
    fun `report generation skipped for component name starting with doc-`() {
        assertTrue(resolver.skipSonarReportGeneration("doc-api"))
    }

    @Test
    fun `report generation skipped for component name starting with doc_`() {
        assertTrue(resolver.skipSonarReportGeneration("doc_api"))
    }

    @Test
    fun `report generation skipped for component name ending with -doc`() {
        assertTrue(resolver.skipSonarReportGeneration("component-doc"))
    }

    @Test
    fun `report generation skipped for component name ending with _doc`() {
        assertTrue(resolver.skipSonarReportGeneration("component_doc"))
    }

    @Test
    fun `report generation skipped for component name ending with -DOC (case-insensitive)`() {
        assertTrue(resolver.skipSonarReportGeneration("component-DOC"))
    }

    @Test
    fun `report generation skipped for component in other-doc-components list`() {
        assertTrue(resolver.skipSonarReportGeneration("other-doc-component"))
    }

    // ── archived ──────────────────────────────────────────────────────────────

    @Test
    fun `report generation skipped for archived component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(archived = true)
        assertTrue(resolver.skipSonarReportGeneration("comp"))
    }

    // ── test-component label ──────────────────────────────────────────────────

    @Test
    fun `report generation skipped for component labelled test-component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("test-component"))
        assertTrue(resolver.skipSonarReportGeneration("comp"))
    }

    // ── should NOT skip ───────────────────────────────────────────────────────

    @Test
    fun `report generation not skipped for regular active component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1()
        assertFalse(resolver.skipSonarReportGeneration("comp"))
    }

    @Test
    fun `report generation not skipped for component in applied-sast list`() {
        every { crsClient.getById("component-with-sast") } returns Fixtures.componentV1()
        assertFalse(resolver.skipSonarReportGeneration("component-with-sast"))
    }

    // ════════════════════════════════════════════════════════════════════════
    // skipSonarGradlePluginExecution
    // ════════════════════════════════════════════════════════════════════════

    // ── should skip ───────────────────────────────────────────────────────

    @Test
    fun `gradle plugin skipped for component in applied-sast list`() {
        assertTrue(resolver.skipSonarGradlePluginExecution("component-with-sast", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for doc component`() {
        assertTrue(resolver.skipSonarGradlePluginExecution("doc-component", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for other-doc-components list`() {
        assertTrue(resolver.skipSonarGradlePluginExecution("other-doc-component", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for archived component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(archived = true)
        assertTrue(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for test-component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("test-component"))
        assertTrue(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for non-gradle build system`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("java"), javaVersion = "17", buildSystem = BuildSystem.MAVEN
        )
        assertTrue(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for non-java non-kotlin component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("python"), javaVersion = "17"
        )
        assertTrue(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin skipped for java gradle component with old javaVersion not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("java"), javaVersion = "8"
        )
        assertTrue(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    // ── should NOT skip ───────────────────────────────────────────────────

    @Test
    fun `gradle plugin not skipped for java gradle component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("java"), javaVersion = "17"
        )
        assertFalse(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin not skipped for kotlin gradle component with javaVersion 21`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("kotlin"), javaVersion = "21"
        )
        assertFalse(resolver.skipSonarGradlePluginExecution("comp", "1.0"))
    }

    @Test
    fun `gradle plugin not skipped for java gradle component in mismatch list`() {
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns Fixtures.detailedComponent(
            labels = setOf("java"), javaVersion = "8"
        )
        assertFalse(resolver.skipSonarGradlePluginExecution("mismatch-java-component", "1.0"))
    }

    @Test
    fun `metarunner skipped for component from external applied-sast list`() {
        val configDir = Files.createTempDirectory("sonar-config-")
        Files.writeString(configDir.resolve("applied-sast.json"), """{"external-sast":{"sonarProjectKey":"SAST_KEY","sonarProjectName":"SAST/NAME"}}""")
        Files.writeString(configDir.resolve("other-doc-components.txt"), "external-doc\n")
        Files.writeString(configDir.resolve("mismatch-java-version.txt"), "external-mismatch\n")

        val externalResolver = SonarExecutionResolver(crsClient, configDir)

        assertTrue(externalResolver.skipSonarMetarunnerExecution("external-sast", "1.0"))
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `report generation skipped for component from external other-doc list`() {
        val configDir = Files.createTempDirectory("sonar-config-")
        Files.writeString(configDir.resolve("applied-sast.json"), """{"external-sast":{"sonarProjectKey":"SAST_KEY","sonarProjectName":"SAST/NAME"}}""")
        Files.writeString(configDir.resolve("other-doc-components.txt"), "external-doc\n")
        Files.writeString(configDir.resolve("mismatch-java-version.txt"), "external-mismatch\n")

        val externalResolver = SonarExecutionResolver(crsClient, configDir)

        assertTrue(externalResolver.skipSonarReportGeneration("external-doc"))
        verify(exactly = 0) { crsClient.getById(any()) }
    }
}
