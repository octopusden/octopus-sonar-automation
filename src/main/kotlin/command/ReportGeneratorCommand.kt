package org.octopusden.octopus.sonar.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.octopusden.octopus.sonar.client.impl.ClassicSonarClient
import org.octopusden.octopus.sonar.client.impl.SonarClientParametersProvider
import org.octopusden.octopus.sonar.resolver.report.QualityGateChecker
import org.octopusden.octopus.sonar.resolver.report.ReportGenerator
import org.octopusden.octopus.sonar.resolver.report.TeamCityNotifier

class ReportGeneratorCommand : CliktCommand(
    name = "generate-sonar-report"
) {
    private val sonarUrl by option(SONAR_SERVER_URL_OPTION, help = "Sonar server URL")
        .required().check("$SONAR_SERVER_URL_OPTION is empty") { it.isNotEmpty() }

    private val componentName by option(COMPONENT_NAME_OPTION, help = "Component name")
        .required().check("$COMPONENT_NAME_OPTION is empty") { it.isNotEmpty() }
    private val componentVersion by option(COMPONENT_VERSION_OPTION, help = "Component version")
        .required().check("$COMPONENT_VERSION_OPTION is empty") { it.isNotEmpty() }

    private val sonarProjectKey by option(SONAR_PROJECT_KEY_OPTION, help = "Sonar project key")
        .required().check("$SONAR_PROJECT_KEY_OPTION is empty") { it.isNotEmpty() }
    private val sonarProjectName by option(SONAR_PROJECT_NAME_OPTION, help = "Sonar project name (format: PROJECT/repo:component)")
        .required().check("$SONAR_PROJECT_NAME_OPTION is empty") { it.isNotEmpty() }
    private val sonarSourceBranch by option(SONAR_SOURCE_BRANCH_OPTION, help = "Sonar source branch")
        .required().check("$SONAR_SOURCE_BRANCH_OPTION is empty") { it.isNotEmpty() }
    private val sonarTargetBranch by option(SONAR_TARGET_BRANCH_OPTION, help = "Sonar target branch")
        .required().check("$SONAR_TARGET_BRANCH_OPTION is empty") { it.isNotEmpty() }

    override fun run() {
        val sonarClient = ClassicSonarClient(
            object : SonarClientParametersProvider {
                override fun getBaseUrl() = sonarUrl
                override fun getUsername() = System.getenv(SONAR_USERNAME_OPTION)
                override fun getPassword() = System.getenv(SONAR_PASSWORD_OPTION)
                override fun getConnectTimeoutInMillis() = 30_000L
                override fun getReadTimeoutInMillis() = 60_000L
            }
        )

        val qualityGateChecker = QualityGateChecker(sonarClient)
        val checkResult = qualityGateChecker.check(sonarProjectKey, sonarSourceBranch)

        val notifier = TeamCityNotifier(sonarUrl, sonarProjectKey, sonarSourceBranch, sonarTargetBranch)
        notifier.buildMessages(checkResult).forEach { echo(it) }

        if (sonarSourceBranch != sonarTargetBranch) {
            echo("Skipping report generation for non production branch $sonarSourceBranch")
            return
        }

        val reportGenerator = ReportGenerator(sonarClient)

        val outputFile = reportGenerator.generate(
            projectKey = sonarProjectKey,
            branch = sonarSourceBranch,
            componentName = componentName,
            componentVersion = componentVersion,
            sonarProjectName = sonarProjectName,
            sonarServerUrl = sonarUrl,
        )

        echo("SAST report generated: ${outputFile.absolutePath}")
    }

    companion object {
        const val SONAR_SERVER_URL_OPTION = "--sonar-server-url"
        const val SONAR_USERNAME_OPTION = "--sonar-username"
        const val SONAR_PASSWORD_OPTION = "--sonar-password"

        const val COMPONENT_NAME_OPTION = "--component-name"
        const val COMPONENT_VERSION_OPTION = "--component-version"

        const val SONAR_PROJECT_KEY_OPTION = "--sonar-project-key"
        const val SONAR_PROJECT_NAME_OPTION = "--sonar-project-name"
        const val SONAR_SOURCE_BRANCH_OPTION = "--sonar-source-branch"
        const val SONAR_TARGET_BRANCH_OPTION = "--sonar-target-branch"
    }
}
