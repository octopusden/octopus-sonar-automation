package org.octopusden.octopus.sonar.util

import java.util.regex.Pattern

/**
 * Parses a Git SSH remote into its uppercase project key and lowercase repository key.
 *
 * Supported formats:
 *   - `ssh://git@bitbucket.example.com[:7999]/PROJECT/repo[.git]`  (SSH URL)
 *   - `git@github.com:PROJECT/repo[.git]`                          (SCP-like)
 */
object VcsSshUrlParser {
    private val SSH_URL_PATTERN = Pattern.compile("ssh://[^@/]+@[^/:]+(?::\\d+)?/([^/]+)/([^/]+?)/?")

    private val SCP_LIKE_PATTERN = Pattern.compile("[^@/]+@[^/:]+:([^/]+)/([^/]+?)/?")

    private val PATTERNS = listOf(SSH_URL_PATTERN, SCP_LIKE_PATTERN)

    fun parseRepository(sshUrl: String): Pair<String, String> {
        val url = sshUrl.trim()

        for (pattern in PATTERNS) {
            val matcher = pattern.matcher(url)
            if (matcher.matches()) {
                return matcher.group(1).uppercase() to
                    matcher.group(2).removeSuffix(".git").lowercase()
            }
        }

        throw IllegalArgumentException(
            "'$sshUrl' does not match any supported SSH remote format. " +
                "Expected 'ssh://user@host[:port]/PROJECT/repo.git' or 'user@host:PROJECT/repo.git'",
        )
    }
}
