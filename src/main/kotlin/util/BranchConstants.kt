package org.octopusden.octopus.sonar.util

/**
 * Shared constants for branch-related logic used across the project.
 */
object BranchConstants {
    /** Prefix that Bitbucket uses for pull-request ref names. */
    const val PULL_REQUEST_BRANCH_MARKER = "pull-requests/"

    /**
     * Target branch for components absent from the Components Registry Service.
     * Deliberately separate from [DEFAULT_BRANCH_CANDIDATES]: that list is a guess order,
     * this is the branch octopusden repositories actually use.
     */
    const val UNREGISTERED_TARGET_BRANCH = "main"

    /** Default branch names to fall back on when no explicit default is configured. */
    val DEFAULT_BRANCH_CANDIDATES = listOf("main", "master")
}
