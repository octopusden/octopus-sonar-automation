package org.octopusden.octopus.sonar.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import org.octopusden.octopus.sonar.resolver.parameters.SonarParametersCalculator
import org.octopusden.octopus.sonar.util.TeamCityEscaper
import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider
import java.nio.file.Path

/**
 * Resolves all Sonar parameters for the current TeamCity build and prints them to be set as
 * TeamCity parameters via service messages
 */
class CalculateSonarParametersCommand : CliktCommand(
    name = "calculate-sonar-params"
) {
    private val teamcityUrl by option(TEAMCITY_URL_OPTION, help = "TeamCity base URL")
        .required().check("$TEAMCITY_URL_OPTION is empty") { it.isNotEmpty() }
    private val teamcityUser by option(TEAMCITY_USER_OPTION, help = "TeamCity username")
        .required().check("$TEAMCITY_USER_OPTION is empty") { it.isNotEmpty() }
    private val teamcityPassword by option(TEAMCITY_PASSWORD_OPTION, help = "TeamCity password")
        .required().check("$TEAMCITY_PASSWORD_OPTION is empty") { it.isNotEmpty() }
    private val teamcityBuildId by option(TEAMCITY_BUILD_ID_OPTION, help = "TeamCity build ID")
        .int().required()

    private val componentsRegistryUrl by option(COMPONENTS_REGISTRY_URL_OPTION, help = "Components Registry Service base URL")
        .required().check("$COMPONENTS_REGISTRY_URL_OPTION is empty") { it.isNotEmpty() }
    private val vcsFacadeUrl by option(VCS_FACADE_URL_OPTION, help = "VCS Facade Service base URL")
        .required().check("$VCS_FACADE_URL_OPTION is empty") { it.isNotEmpty() }

    private val componentName by option(COMPONENT_NAME_OPTION, help = "Component name")
        .required().check("$COMPONENT_NAME_OPTION is empty") { it.isNotEmpty() }
    private val componentVersion by option(COMPONENT_VERSION_OPTION, help = "Component version")
        .required().check("$COMPONENT_VERSION_OPTION is empty") { it.isNotEmpty() }
    private val sonarConfigDir by option(SONAR_CONFIG_DIR_OPTION, help = "Directory containing sonar skip-list files")
        .required().check("$SONAR_CONFIG_DIR_OPTION is empty") { it.isNotEmpty() }

    override fun run() {
        val teamcityClient = TeamcityRestClient(teamcityUrl, teamcityUser, teamcityPassword)
        val crsClient = ClassicComponentsRegistryServiceClient(
            object : ClassicComponentsRegistryServiceClientUrlProvider {
                override fun getApiUrl() = componentsRegistryUrl
            }
        )
        val vcsFacadeClient = ClassicVcsFacadeClient(
            object : VcsFacadeClientParametersProvider {
                override fun getApiUrl() = vcsFacadeUrl
                override fun getTimeRetryInMillis() = 180000
            }
        )
        val calculator = SonarParametersCalculator(
            teamcityClient = teamcityClient,
            crsClient = crsClient,
            vcsFacadeClient = vcsFacadeClient,
            componentName = componentName,
            componentVersion = componentVersion,
            teamcityBuildId = teamcityBuildId,
            sonarConfigDir = Path.of(sonarConfigDir),
        )

        val params = calculator.calculate()

        setTeamcityParameter(SONAR_PROJECT_KEY_PARAMETER, params.sonarProjectKey)
        setTeamcityParameter(SONAR_PROJECT_NAME_PARAMETER, params.sonarProjectName)
        setTeamcityParameter(SONAR_SOURCE_BRANCH_PARAMETER, params.sonarSourceBranch)
        setTeamcityParameter(SONAR_TARGET_BRANCH_PARAMETER, params.sonarTargetBranch)
        setTeamcityParameter(SONAR_SERVER_ID_PARAMETER, params.sonarServerId)
        setTeamcityParameter(SONAR_SERVER_URL_PARAMETER, params.sonarServerUrl)
        setTeamcityParameter(SONAR_SERVER_TOKEN_PARAMETER, params.sonarServerToken)
        setTeamcityParameter(SONAR_EXTRA_PARAMETERS_PARAMETER, params.sonarExtraParameters)
        setTeamcityParameter(SKIP_SONAR_METARUNNER_EXECUTION_PARAMETER, params.skipSonarMetarunnerExecution.toString())
        setTeamcityParameter(SKIP_SONAR_REPORT_GENERATION_PARAMETER, params.skipSonarReportGeneration.toString())
        setTeamcityParameter(SONAR_PLUGIN_TASK_PARAMETER, params.sonarPluginTask)
    }

    private fun setTeamcityParameter(name: String, value: String) {
        echo("##teamcity[setParameter name='$name' value='${TeamCityEscaper.escape(value)}']")
    }
    companion object {
        const val TEAMCITY_URL_OPTION = "--teamcity-url"
        const val TEAMCITY_USER_OPTION = "--teamcity-user"
        const val TEAMCITY_PASSWORD_OPTION = "--teamcity-password"
        const val TEAMCITY_BUILD_ID_OPTION = "--teamcity-build-id"

        const val COMPONENTS_REGISTRY_URL_OPTION = "--components-registry-url"
        const val VCS_FACADE_URL_OPTION = "--vcs-facade-url"

        const val COMPONENT_NAME_OPTION = "--component-name"
        const val COMPONENT_VERSION_OPTION = "--component-version"
        const val SONAR_CONFIG_DIR_OPTION = "--sonar-config-dir"

        const val SONAR_PROJECT_KEY_PARAMETER = "SONAR_PROJECT_KEY"
        const val SONAR_PROJECT_NAME_PARAMETER = "SONAR_PROJECT_NAME"
        const val SONAR_SOURCE_BRANCH_PARAMETER = "SONAR_SOURCE_BRANCH"
        const val SONAR_TARGET_BRANCH_PARAMETER = "SONAR_TARGET_BRANCH"
        const val SONAR_SERVER_ID_PARAMETER = "SONAR_SERVER_ID"
        const val SONAR_SERVER_URL_PARAMETER = "SONAR_SERVER_URL"
        const val SONAR_SERVER_TOKEN_PARAMETER = "SONAR_SERVER_TOKEN"
        const val SONAR_EXTRA_PARAMETERS_PARAMETER = "SONAR_EXTRA_PARAMETERS"
        const val SKIP_SONAR_METARUNNER_EXECUTION_PARAMETER = "SKIP_SONAR_METARUNNER_EXECUTION"
        const val SKIP_SONAR_REPORT_GENERATION_PARAMETER = "SKIP_SONAR_REPORT_GENERATION"
        const val SONAR_PLUGIN_TASK_PARAMETER = "SONAR_PLUGIN_TASK"
    }
}
