package org.octopusden.octopus.sonar.resolver

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistrationResolver
import org.octopusden.octopus.sonar.test.Fixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentRegistrationResolverTest {
    private lateinit var crsClient: ComponentsRegistryServiceClient
    private lateinit var resolver: ComponentRegistrationResolver

    @BeforeTest
    fun setUp() {
        crsClient = mockk()
        resolver = ComponentRegistrationResolver(crsClient)
    }

    @Test
    fun `component present in the registry is REGISTERED`() {
        every { crsClient.getById("dms-getver") } returns Fixtures.componentV1()

        assertEquals(ComponentRegistration.REGISTERED, resolver.resolve("dms-getver"))
    }

    @Test
    fun `component absent from the registry is UNREGISTERED`() {
        every { crsClient.getById("octopus-external-systems-client") } throws NotFoundException("not found")

        assertEquals(
            ComponentRegistration.UNREGISTERED,
            resolver.resolve("octopus-external-systems-client"),
        )
    }

    @Test
    fun `registry failure other than not-found propagates`() {
        every { crsClient.getById("dms-getver") } throws RuntimeException("connection refused")

        assertThrows<RuntimeException> { resolver.resolve("dms-getver") }
    }
}
