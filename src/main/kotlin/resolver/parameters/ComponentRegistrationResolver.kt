package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.slf4j.LoggerFactory

/**
 * Whether a component exists in the Components Registry Service.
 *
 * Octopus open-source components are built on the same TeamCity chains but are not registered,
 * so every registry-derived parameter has to fall back to a fixed value for them.
 */
enum class ComponentRegistration {
    REGISTERED,
    UNREGISTERED,
}

/**
 * Determines once per invocation whether a component exists in the Components Registry Service.
 */
class ComponentRegistrationResolver(
    private val crsClient: ComponentsRegistryServiceClient,
) {
    /**
     * Returns [ComponentRegistration.UNREGISTERED] when the registry reports the component as
     * unknown. Any other registry failure propagates — an outage must not be mistaken for an
     * unregistered component.
     */
    fun resolve(componentName: String): ComponentRegistration =
        try {
            crsClient.getById(componentName)
            ComponentRegistration.REGISTERED
        } catch (_: NotFoundException) {
            logger.info("$componentName is not registered in the Components Registry Service")
            ComponentRegistration.UNREGISTERED
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ComponentRegistrationResolver::class.java)
    }
}
