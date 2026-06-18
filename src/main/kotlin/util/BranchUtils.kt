package org.octopusden.octopus.sonar.util

import org.octopusden.octopus.sonar.util.BranchConstants.PULL_REQUEST_BRANCH_MARKER

/**
 * Normalizes a raw Git ref name into a plain branch name by removing well-known
 * prefixes and suffixes that TeamCity may add
 */
fun String.normalizedBranch(): String {
    var result = this
        .removePrefix("refs/")
        .removePrefix("heads/")
        .removeSuffix("/")

    if (result.startsWith(PULL_REQUEST_BRANCH_MARKER)) {
        result = result
            .removeSuffix("/from")
            .removeSuffix("/to")
    }

    return result
}
