package org.octopusden.octopus.sonar.util

/**
 * Shared constants for branch-related logic used across the project.
 */
object BranchConstants {
    /** Prefix that Bitbucket uses for pull-request ref names. */
    const val PULL_REQUEST_BRANCH_MARKER = "pull-requests/"

    /** Default branch names to fall back on when no explicit default is configured. */
    val DEFAULT_BRANCH_CANDIDATES = listOf("main", "master")

    /**
     * Returns `true` when [branch] is a "native" TeamCity Pull Request build feature branch —
     * i.e. exactly `pull-requests/<id>` with no further path segments.
     *
     * Branches with a suffix such as `pull-requests/<id>/from` (produced by a branch-filter
     * specification rather than the TC Pull Request build feature) return `false`, because
     * `%teamcity.pullRequest.*` parameters are not available for those builds.
     */
    fun isPullRequestBranch(branch: String): Boolean =
        branch.removePrefix(PULL_REQUEST_BRANCH_MARKER).let { prId ->
            prId != branch &&
            prId.isNotEmpty() &&
            !prId.contains('/') &&
            prId.all(Char::isDigit)
        }
}

