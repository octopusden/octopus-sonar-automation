package org.octopusden.octopus.sonar

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import org.octopusden.octopus.sonar.command.CalculateSonarParametersCommand
import org.octopusden.octopus.sonar.command.ReportGeneratorCommand

/**
 * Root CLI command for the sonar-automation tool.
 *
 * Sub-commands:
 * - [CalculateSonarParametersCommand] (`calculate-sonar-params`) — resolves and prints all Sonar
 *   parameters for the current TeamCity build.
 * - [ReportGeneratorCommand] (`generate-sonar-report`) — generates a SAST HTML report
 *   from SonarQube analysis results.
 *
 * Run with `--help` to see available sub-commands.
 */
class SonarAutomationCli : CliktCommand(
    name = "sonar-automation"
) {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    SonarAutomationCli()
        .subcommands(
            CalculateSonarParametersCommand(),
            ReportGeneratorCommand(),
        )
        .main(args)
}