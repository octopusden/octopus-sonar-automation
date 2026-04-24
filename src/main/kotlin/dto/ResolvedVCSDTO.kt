package org.octopusden.octopus.sonar.dto

/**
 * Holds the resolved VCS context for the current build:
 * the matched commit stamp, candidate default/target branches,
 * and the parsed Bitbucket project & repository keys.
 */
data class ResolvedVCSDTO(
    val commit: CommitStampDTO,
    val defaultBranches: List<String>,
    val bbProjectKey: String,
    val bbRepositoryKey: String
)
