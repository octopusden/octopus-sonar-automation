package org.octopusden.octopus.sonar.util

/**
 * Normalizes a raw Git ref name into a plain branch name by removing well-known
 * prefixes and suffixes that TeamCity may add
 */
fun String.normalizedBranch(): String {
    var result = this
        .removePrefix("refs/")
        .removePrefix("heads/")
        .removeSuffix("/")

    if (result.startsWith("pull-requests/")) {
        result = result
            .removeSuffix("/from")
            .removeSuffix("/to")
    }

    return result
}
