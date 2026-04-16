package org.octopusden.octopus.sonar.client.dto

data class QualityGateProjectStatusDTO(
    val status: String
)

data class QualityGateResponseDTO(
    val projectStatus: QualityGateProjectStatusDTO
)

