package org.octopusden.octopus.sonar.dto

/**
 * The complete set of Sonar parameters computed for a single TeamCity build.
 *
 * @param sonarProjectKey              Unique Sonar project key in the format
 *                                     `{BB_PROJECT_KEY}_{BB_REPOSITORY_KEY}_{COMPONENT_NAME}` for regular resolution,
 *                                     or an override key from `applied-sast.json`.
 * @param sonarProjectName             Human-readable Sonar project name in the format
 *                                     `{BB_PROJECT_KEY}/{BB_REPOSITORY_KEY}:{COMPONENT_NAME}` for regular resolution,
 *                                     or an override name from `applied-sast.json`.
 * @param sonarSourceBranch            The branch currently being analyzed (or the TeamCity
 *                                     pull-request source branch parameter for PR builds).
 * @param sonarTargetBranch            The base branch to compare against (or the TeamCity
 *                                     pull-request target branch parameter for PR builds).
 * @param sonarServerId                TeamCity parameter name that holds the Sonar server ID.
 * @param sonarServerUrl               TeamCity parameter name that holds the Sonar server URL.
 * @param sonarServerToken             TeamCity parameter name that holds the Sonar authentication token.
 * @param sonarExtraParameters         The `-Dsonar.*` flags string passed to the Sonar scanner.
 *                                     Empty when applied-SAST override is used.
 * @param skipSonarMetarunnerExecution Whether the Sonar metarunner step should be skipped entirely.
 * @param skipSonarReportGeneration    Whether the Sonar report generation step should be skipped.
 * @param sonarGradleTask              The Gradle task to run for Sonar analysis (`sonar` when applicable, empty otherwise).
 */
data class SonarParametersDTO(
    val sonarProjectKey: String,
    val sonarProjectName: String,
    val sonarSourceBranch: String,
    val sonarTargetBranch: String,
    val sonarServerId: String,
    val sonarServerUrl: String,
    val sonarServerToken: String,
    val sonarExtraParameters: String,
    val skipSonarMetarunnerExecution: Boolean,
    val skipSonarReportGeneration: Boolean,
    val sonarGradleTask: String
)
