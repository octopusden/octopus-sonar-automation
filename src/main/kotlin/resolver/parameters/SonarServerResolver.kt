package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO.Companion.DEVELOPER_LABELS

/**
 * Selects the appropriate SonarQube server (Developer or Community Edition)
 */
class SonarServerResolver(
    private val crsClient: ComponentsRegistryServiceClient,
) {
    /**
     * Returns [SonarServerParametersDTO.DEVELOPER] when the component's labels contain
     * any of [DEVELOPER_LABELS] (`c`, `cpp`, `objective_c`, `swift`) — those languages require
     * SonarQube Developer Edition or above.
     * Otherwise, returns [SonarServerParametersDTO.COMMUNITY].
     *
     * An unregistered component has no labels to inspect and always uses Community Edition.
     */
    fun resolveSonarServer(
        componentName: String,
        registration: ComponentRegistration,
    ): SonarServerParametersDTO {
        if (registration == ComponentRegistration.UNREGISTERED) {
            return SonarServerParametersDTO.COMMUNITY
        }

        val labels = crsClient.getById(componentName).labels

        return if (labels.any { it in DEVELOPER_LABELS }) {
            SonarServerParametersDTO.DEVELOPER
        } else {
            SonarServerParametersDTO.COMMUNITY
        }
    }
}
