package org.octopusden.octopus.sonar.util

/**
 * Normalizes a raw Git ref name into a plain branch name by removing well-known
 * prefixes that TeamCity may add (`refs/`, `heads/`) and trailing slashes.
 *
 * Pull-request branches are kept intact — including the `/from` or `/to` suffixes
 * that Bitbucket/TeamCity adds when a build is triggered via a branch-filter rather
 * than the TC Pull Request build feature. Keeping these suffixes allows callers to
 * distinguish `pull-requests/<id>` (native PR build, has `%teamcity.pullRequest.*`
 * parameters) from `pull-requests/<id>/from` (branch-filter build, does not).
 * Use [BranchConstants.isPullRequestBranch] for that check.
 */
fun String.normalizedBranch(): String =
    this
        .removePrefix("refs/")
        .removePrefix("heads/")
        .removeSuffix("/")
