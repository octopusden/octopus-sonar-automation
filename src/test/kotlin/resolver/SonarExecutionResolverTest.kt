package org.octopusden.octopus.sonar.resolver

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.REGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.UNREGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.SonarExecutionResolver
import org.octopusden.octopus.sonar.test.Fixtures
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                ?.let {
                    java.nio.file.Path
                        .of(it)
                }
                ?: error("Missing test resource directory: sonar-config")
        resolver = SonarExecutionResolver(crsClient, configDir)
    }

    // ════════════════════════════════════════════════════════════════════════
    // skipSonarMetarunnerExecution
    // ════════════════════════════════════════════════════════════════════════

    // ── applied-sast list ─────────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for component in applied-sast list`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skip for applied-sast component does not call CRS`() {
        resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0", REGISTERED)
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `metarunner skipped for component in applied-sast list (JSON-based config)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skip for applied-sast component (JSON-based config) does not call CRS`() {
        resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0", REGISTERED)
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
        assertTrue(resolver.skipSonarMetarunnerExecution("doc-component", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component name starting with doc_`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("doc_component", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component name starting with DOC_ (case-insensitive)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("DOC_format", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component name ending with -doc`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-doc", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component name ending with _doc`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component_doc", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component name ending with -DOC (case-insensitive)`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-DOC", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner not skipped for component name starting with doc but not doc- prefix`() {
        every { crsClient.getDetailedComponent("docker-registry", "1.0") } returns Fixtures.detailedComponent()
        assertFalse(resolver.skipSonarMetarunnerExecution("docker-registry", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component in other-doc-components list`() {
        // "other-doc-component" is in other-doc-components.txt
        assertTrue(resolver.skipSonarMetarunnerExecution("other-doc-component", "1.0", REGISTERED))
    }

    // ── archived ──────────────────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for archived component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(archived = true)
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    // ── test-component label ──────────────────────────────────────────────────

    @Test
    fun `metarunner skipped for component labelled test-component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("test-component"))
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    // ── java/kotlin with modern JDK ───────────────────────────────────────────

    @Test
    fun `metarunner skipped for java component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "17")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for java component with javaVersion 21`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "21")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for kotlin component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("kotlin"), javaVersion = "17")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for java component with javaVersion 25`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "25")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for java component with future javaVersion beyond current LTS releases`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "29")
        assertTrue(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for java component in mismatch-java-version list`() {
        // "mismatch-java-component" is the first entry in mismatch-java-version.txt
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "21")
        assertTrue(resolver.skipSonarMetarunnerExecution("mismatch-java-component", "1.0", REGISTERED))
    }

    // ── should NOT skip ───────────────────────────────────────────────────────

    @Test
    fun `metarunner not skipped for regular active component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent()
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner not skipped for java component on old javaVersion not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "8")
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner not skipped for java component with no javaVersion and not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("java"))
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for java component with old javaVersion but in mismatch list`() {
        // mismatch-java-component is in mismatch-java-version.txt, meaning it actually uses modern JDK
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("java"), javaVersion = "8")
        assertTrue(resolver.skipSonarMetarunnerExecution("mismatch-java-component", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner not skipped for non-java non-kotlin component even with modern javaVersion`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(labels = setOf("python"), javaVersion = "17")
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner not skipped for java component with modern javaVersion but non-plugin-eligible build system`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "17",
                buildSystem = BuildSystem.PROVIDED,
            )
        assertFalse(resolver.skipSonarMetarunnerExecution("comp", "1.0", REGISTERED))
    }

    // ════════════════════════════════════════════════════════════════════════
    // skipSonarReportGeneration
    // ════════════════════════════════════════════════════════════════════════

    // ── documentation components ───────────────────────────────────────────────

    @Test
    fun `report generation skipped for component name starting with doc-`() {
        assertTrue(resolver.skipSonarReportGeneration("doc-api", REGISTERED))
    }

    @Test
    fun `report generation skipped for component name starting with doc_`() {
        assertTrue(resolver.skipSonarReportGeneration("doc_api", REGISTERED))
    }

    @Test
    fun `report generation skipped for component name ending with -doc`() {
        assertTrue(resolver.skipSonarReportGeneration("component-doc", REGISTERED))
    }

    @Test
    fun `report generation skipped for component name ending with _doc`() {
        assertTrue(resolver.skipSonarReportGeneration("component_doc", REGISTERED))
    }

    @Test
    fun `report generation skipped for component name ending with -DOC (case-insensitive)`() {
        assertTrue(resolver.skipSonarReportGeneration("component-DOC", REGISTERED))
    }

    @Test
    fun `report generation skipped for component in other-doc-components list`() {
        assertTrue(resolver.skipSonarReportGeneration("other-doc-component", REGISTERED))
    }

    // ── archived ──────────────────────────────────────────────────────────────

    @Test
    fun `report generation skipped for archived component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(archived = true)
        assertTrue(resolver.skipSonarReportGeneration("comp", REGISTERED))
    }

    // ── test-component label ──────────────────────────────────────────────────

    @Test
    fun `report generation skipped for component labelled test-component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("test-component"))
        assertTrue(resolver.skipSonarReportGeneration("comp", REGISTERED))
    }

    // ── should NOT skip ───────────────────────────────────────────────────────

    @Test
    fun `report generation not skipped for regular active component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1()
        assertFalse(resolver.skipSonarReportGeneration("comp", REGISTERED))
    }

    @Test
    fun `report generation not skipped for component in applied-sast list`() {
        every { crsClient.getById("component-with-sast") } returns Fixtures.componentV1()
        assertFalse(resolver.skipSonarReportGeneration("component-with-sast", REGISTERED))
    }

    // ════════════════════════════════════════════════════════════════════════
    // resolveSonarPluginBuildSystem
    // ════════════════════════════════════════════════════════════════════════

    // ── should skip (return null) ─────────────────────────────────────────

    @Test
    fun `plugin skipped for component in applied-sast list`() {
        assertNull(resolver.resolveSonarPluginBuildSystem("component-with-sast", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for doc component`() {
        assertNull(resolver.resolveSonarPluginBuildSystem("doc-component", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for other-doc-components list`() {
        assertNull(resolver.resolveSonarPluginBuildSystem("other-doc-component", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for archived component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(archived = true)
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for test-component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns Fixtures.detailedComponent(labels = setOf("test-component"))
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for non-gradle non-maven build system`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "17",
                buildSystem = BuildSystem.PROVIDED,
            )
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for non-java non-kotlin component`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("python"),
                javaVersion = "17",
                buildSystem = BuildSystem.GRADLE,
            )
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for java gradle component with old javaVersion not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "8",
                buildSystem = BuildSystem.GRADLE,
            )
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `plugin skipped for java maven component with old javaVersion not in mismatch list`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "8",
                buildSystem = BuildSystem.MAVEN,
            )
        assertNull(resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    // ── should return GRADLE ──────────────────────────────────────────────

    @Test
    fun `returns GRADLE for java gradle component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "17",
                buildSystem = BuildSystem.GRADLE,
            )
        assertEquals(BuildSystem.GRADLE, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns GRADLE for kotlin gradle component with javaVersion 21`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("kotlin"),
                javaVersion = "21",
                buildSystem = BuildSystem.GRADLE,
            )
        assertEquals(BuildSystem.GRADLE, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns GRADLE for java gradle component with javaVersion 25`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "25",
                buildSystem = BuildSystem.GRADLE,
            )
        assertEquals(BuildSystem.GRADLE, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns GRADLE for java gradle component in mismatch list`() {
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "8",
                buildSystem = BuildSystem.GRADLE,
            )
        assertEquals(BuildSystem.GRADLE, resolver.resolveSonarPluginBuildSystem("mismatch-java-component", "1.0", REGISTERED))
    }

    // ── should return MAVEN ───────────────────────────────────────────────

    @Test
    fun `returns MAVEN for java maven component with javaVersion 17`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "17",
                buildSystem = BuildSystem.MAVEN,
            )
        assertEquals(BuildSystem.MAVEN, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns MAVEN for kotlin maven component with javaVersion 21`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("kotlin"),
                javaVersion = "21",
                buildSystem = BuildSystem.MAVEN,
            )
        assertEquals(BuildSystem.MAVEN, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns MAVEN for java maven component with javaVersion 25`() {
        every { crsClient.getDetailedComponent("comp", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "25",
                buildSystem = BuildSystem.MAVEN,
            )
        assertEquals(BuildSystem.MAVEN, resolver.resolveSonarPluginBuildSystem("comp", "1.0", REGISTERED))
    }

    @Test
    fun `returns MAVEN for java maven component in mismatch list`() {
        every { crsClient.getDetailedComponent("mismatch-java-component", "1.0") } returns
            Fixtures.detailedComponent(
                labels = setOf("java"),
                javaVersion = "8",
                buildSystem = BuildSystem.MAVEN,
            )
        assertEquals(BuildSystem.MAVEN, resolver.resolveSonarPluginBuildSystem("mismatch-java-component", "1.0", REGISTERED))
    }

    @Test
    fun `metarunner skipped for component from external applied-sast list`() {
        val configDir = Files.createTempDirectory("sonar-config-")
        Files.writeString(
            configDir.resolve("applied-sast.json"),
            """{"external-sast":{"sonarProjectKey":"SAST_KEY","sonarProjectName":"SAST/NAME"}}""",
        )
        Files.writeString(configDir.resolve("other-doc-components.txt"), "external-doc\n")
        Files.writeString(configDir.resolve("mismatch-java-version.txt"), "external-mismatch\n")

        val externalResolver = SonarExecutionResolver(crsClient, configDir)

        assertTrue(externalResolver.skipSonarMetarunnerExecution("external-sast", "1.0", REGISTERED))
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `report generation skipped for component from external other-doc list`() {
        val configDir = Files.createTempDirectory("sonar-config-")
        Files.writeString(
            configDir.resolve("applied-sast.json"),
            """{"external-sast":{"sonarProjectKey":"SAST_KEY","sonarProjectName":"SAST/NAME"}}""",
        )
        Files.writeString(configDir.resolve("other-doc-components.txt"), "external-doc\n")
        Files.writeString(configDir.resolve("mismatch-java-version.txt"), "external-mismatch\n")

        val externalResolver = SonarExecutionResolver(crsClient, configDir)

        assertTrue(externalResolver.skipSonarReportGeneration("external-doc", REGISTERED))
        verify(exactly = 0) { crsClient.getById(any()) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // unregistered components
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `metarunner runs for an unregistered component`() {
        assertFalse(resolver.skipSonarMetarunnerExecution("octopus-comp", "1.0", UNREGISTERED))
    }

    @Test
    fun `report generation runs for an unregistered component`() {
        assertFalse(resolver.skipSonarReportGeneration("octopus-comp", UNREGISTERED))
    }

    @Test
    fun `no sonar plugin build system for an unregistered component`() {
        assertNull(resolver.resolveSonarPluginBuildSystem("octopus-comp", "1.0", UNREGISTERED))
    }

    @Test
    fun `unregistered component does not query the registry`() {
        resolver.skipSonarMetarunnerExecution("octopus-comp", "1.0", UNREGISTERED)
        resolver.skipSonarReportGeneration("octopus-comp", UNREGISTERED)
        resolver.resolveSonarPluginBuildSystem("octopus-comp", "1.0", UNREGISTERED)
        verify(exactly = 0) { crsClient.getById(any()) }
        verify(exactly = 0) { crsClient.getDetailedComponent(any(), any()) }
    }

    @Test
    fun `applied-sast list still wins for an unregistered component`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("component-with-sast", "1.0", UNREGISTERED))
        assertNull(resolver.resolveSonarPluginBuildSystem("component-with-sast", "1.0", UNREGISTERED))
    }

    @Test
    fun `doc prefix still wins for an unregistered component`() {
        assertTrue(resolver.skipSonarMetarunnerExecution("doc-octopus-comp", "1.0", UNREGISTERED))
        assertTrue(resolver.skipSonarReportGeneration("doc-octopus-comp", UNREGISTERED))
    }
}
