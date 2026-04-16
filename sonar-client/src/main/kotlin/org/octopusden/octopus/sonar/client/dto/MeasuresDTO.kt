package org.octopusden.octopus.sonar.client.dto

data class MeasureDTO(
    val metric: String,
    val value: String?,
    val bestValue: Boolean?
)

data class ComponentMeasuresDTO(
    val key: String,
    val name: String,
    val measures: List<MeasureDTO>
)

data class MeasuresResponseDTO(
    val component: ComponentMeasuresDTO
)

