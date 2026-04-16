package org.octopusden.octopus.sonar.util

/**
 * Escapes a string value for use inside TeamCity service message attributes.
 *
 * TeamCity requires the following characters to be escaped with a leading `|`:
 * `|`, `'`, `[`, `]`, `\n`, `\r`.
 *
 * @see <a href="https://www.jetbrains.com/help/teamcity/service-messages.html#Escaped+Values">TeamCity Escaped Values</a>
 */
object TeamCityEscaper {

    fun escape(value: String): String = value
        .replace("|", "||")
        .replace("'", "|'")
        .replace("\n", "|n")
        .replace("\r", "|r")
        .replace("[", "|[")
        .replace("]", "|]")
}

