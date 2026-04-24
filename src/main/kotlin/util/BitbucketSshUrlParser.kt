package org.octopusden.octopus.sonar.util

import java.util.regex.Pattern

/**
 * Parses a Bitbucket SSH URL into its uppercase project key and lowercase repository key.
 *
 * Supported formats:
 *   - `ssh://git@bitbucket.example.com/PROJECT/repo.git`  (SSH URL)
 */
object BitbucketSshUrlParser {

    fun parseRepository(sshUrl: String): Pair<String, String> {
        val sshMatcher = SSH_URL_PATTERN.matcher(sshUrl)
        if (sshMatcher.matches()) {
            return sshMatcher.group(1).uppercase() to
                   sshMatcher.group(2).removeSuffix(".git").lowercase()
        }

        throw IllegalArgumentException(
            "'$sshUrl' does not match any supported SSH URL format. " +
            "Expected 'ssh://user@host/PROJECT/repo.git'"
        )
    }

    /** Matches: ssh://user@host/PROJECT/repo.git */
    private val SSH_URL_PATTERN = Pattern.compile("ssh://[^@]+@[^/]+/([^/]+)/([^/]+)")
}
