package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO.Companion.DEVELOPER_LABELS
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient

/**
 * Selects the appropriate SonarQube server (Developer or Community Edition)
 */
class SonarServerResolver(
    private val crsClient: ComponentsRegistryServiceClient
) {

    /**
     * Returns [SonarServerParametersDTO.DEVELOPER] when the component's labels contain
     * any of [DEVELOPER_LABELS] (`c`, `cpp`, `objective_c`, `swift`) — those languages require
     * SonarQube Developer Edition or above.
     * Otherwise, returns [SonarServerParametersDTO.COMMUNITY].
     */
    fun resolveSonarServer(componentName: String): SonarServerParametersDTO {
        val labels = crsClient.getById(componentName).labels

        return if (labels.any { it in DEVELOPER_LABELS }) {
            SonarServerParametersDTO.DEVELOPER
        } else {
            SonarServerParametersDTO.COMMUNITY
        }
    }

}