package org.octopusden.octopus.sonar.client.dto

data class PagingDTO(
    val pageIndex: Int,
    val pageSize: Int,
    val total: Int
)
