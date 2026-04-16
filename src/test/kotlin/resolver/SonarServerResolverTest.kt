package org.octopusden.octopus.sonar.resolver

import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.resolver.parameters.SonarServerResolver
import org.octopusden.octopus.sonar.test.Fixtures
import io.mockk.every
import io.mockk.mockk
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SonarServerResolverTest {

    private lateinit var crsClient: ComponentsRegistryServiceClient
    private lateinit var resolver: SonarServerResolver

    @BeforeTest
    fun setUp() {
        crsClient = mockk()
        resolver = SonarServerResolver(crsClient)
    }

    // ── Community Edition ─────────────────────────────────────────────────────

    @Test
    fun `returns COMMUNITY for component with no labels`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1()
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns COMMUNITY for java component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("java"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns COMMUNITY for kotlin component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("kotlin"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns COMMUNITY for python component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("python"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp"))
    }

    // ── Developer Edition ─────────────────────────────────────────────────────

    @Test
    fun `returns DEVELOPER for component labelled c`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("c"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns DEVELOPER for component labelled cpp`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("cpp"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns DEVELOPER for component labelled objective_c`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("objective_c"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns DEVELOPER for component labelled swift`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("swift"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp"))
    }

    @Test
    fun `returns DEVELOPER when developer label is mixed with other labels`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("java", "cpp", "some-other"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp"))
    }

    // ── parameter names ───────────────────────────────────────────────────────

    @Test
    fun `DEVELOPER instance holds correct TeamCity parameter names`() {
        assertEquals("%SONAR_DEVELOPER_ID%", SonarServerParametersDTO.DEVELOPER.id)
        assertEquals("%SONAR_DEVELOPER_URL%", SonarServerParametersDTO.DEVELOPER.url)
    }

    @Test
    fun `COMMUNITY instance holds correct TeamCity parameter names`() {
        assertEquals("%SONAR_COMMUNITY_ID%", SonarServerParametersDTO.COMMUNITY.id)
        assertEquals("%SONAR_COMMUNITY_URL%", SonarServerParametersDTO.COMMUNITY.url)
    }
}
