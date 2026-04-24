package org.octopusden.octopus.sonar.dto

/**
 * Represents a single revision entry extracted from a TeamCity build's revision list
 */
data class CommitStampDTO(
    val cid: String,
    val branch: String,
    val vcsUrl: String
)