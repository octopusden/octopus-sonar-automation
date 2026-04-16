package org.octopusden.octopus.sonar.client.dto

import java.util.Date

data class IssueImpactDTO(
    val softwareQuality: String,
    val severity: String
)

data class IssueDTO(
    val key: String,
    val rule: String,
    val severity: String,
    val component: String,
    val line: Int,
    val status: String,
    val message: String,
    val effort: String,
    val author: String,
    val creationDate: Date,
    val updateDate: Date,
    val type: String,
    val scope: String,
    val impacts: List<IssueImpactDTO>
)

data class IssuesResponseDTO(
    val paging: PagingDTO,
    val effortTotal: Int?,
    val issues: List<IssueDTO>
)

