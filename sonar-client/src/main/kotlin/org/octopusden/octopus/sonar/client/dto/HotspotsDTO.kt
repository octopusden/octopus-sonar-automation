package org.octopusden.octopus.sonar.client.dto

data class HotspotDTO(
    val key: String,
    val component: String,
    val project: String,
    val securityCategory: String,
    val vulnerabilityProbability: String?,
    val status: String,
    val line: Int?,
    val message: String,
    val author: String?,
    val creationDate: String?,
    val updateDate: String?,
    val ruleKey: String?
)

data class HotspotsResponseDTO(
    val paging: PagingDTO,
    val hotspots: List<HotspotDTO>
)

