package org.octopusden.octopus.sonar.resolver

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.REGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.UNREGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.SonarServerResolver
import org.octopusden.octopus.sonar.test.Fixtures
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
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns COMMUNITY for java component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("java"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns COMMUNITY for kotlin component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("kotlin"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns COMMUNITY for python component`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("python"))
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("comp", REGISTERED))
    }

    // ── Developer Edition ─────────────────────────────────────────────────────

    @Test
    fun `returns DEVELOPER for component labelled c`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("c"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns DEVELOPER for component labelled cpp`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("cpp"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns DEVELOPER for component labelled objective_c`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("objective_c"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns DEVELOPER for component labelled swift`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("swift"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp", REGISTERED))
    }

    @Test
    fun `returns DEVELOPER when developer label is mixed with other labels`() {
        every { crsClient.getById("comp") } returns Fixtures.componentV1(labels = listOf("java", "cpp", "some-other"))
        assertEquals(SonarServerParametersDTO.DEVELOPER, resolver.resolveSonarServer("comp", REGISTERED))
    }

    // ── unregistered components ───────────────────────────────────────────────

    @Test
    fun `returns COMMUNITY for an unregistered component`() {
        assertEquals(SonarServerParametersDTO.COMMUNITY, resolver.resolveSonarServer("octopus-comp", UNREGISTERED))
    }

    @Test
    fun `does not query the registry for an unregistered component`() {
        resolver.resolveSonarServer("octopus-comp", UNREGISTERED)
        verify(exactly = 0) { crsClient.getById(any()) }
    }

    // ── parameter names ───────────────────────────────────────────────────────

    @Test
    fun `DEVELOPER instance holds correct TeamCity parameter names`() {
        assertEquals("%SONAR_DEVELOPER_ID%", SonarServerParametersDTO.DEVELOPER.id)
        assertEquals("%SONAR_DEVELOPER_URL%", SonarServerParametersDTO.DEVELOPER.url)
        assertEquals("%SONAR_DEVELOPER_TOKEN%", SonarServerParametersDTO.DEVELOPER.token)
    }

    @Test
    fun `COMMUNITY instance holds correct TeamCity parameter names`() {
        assertEquals("%SONAR_COMMUNITY_ID%", SonarServerParametersDTO.COMMUNITY.id)
        assertEquals("%SONAR_COMMUNITY_URL%", SonarServerParametersDTO.COMMUNITY.url)
        assertEquals("%SONAR_COMMUNITY_TOKEN%", SonarServerParametersDTO.COMMUNITY.token)
    }
}
