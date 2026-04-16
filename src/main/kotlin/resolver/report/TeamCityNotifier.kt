package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.dto.QualityGateCheckResult

/**
 * Translates a [QualityGateCheckResult] into TeamCity service messages.
 *
 * Mirrors the logic from PrevMetarunner:
 * - Quality gate ERROR → `buildProblem` (fails the build)
 * - New issues found → `buildStatus` warning with issue count
 * - On master with failed metric ratings → `buildStatus` warning with rating names
 */
class TeamCityNotifier(
    private val sonarServerUrl: String,
    private val projectKey: String,
    private val branch: String,
) {
    /**
     * Produces a list of TeamCity service messages based on the quality gate check result.
     *
     * @param result the quality gate check result
     * @return list of service messages to print to stdout
     */
    fun buildMessages(result: QualityGateCheckResult): List<String> {
        val messages = mutableListOf<String>()

        val baseUrl = sonarServerUrl.trimEnd('/')
        val branchParam = branchOrPrParam(branch)
        val dashboardLink = "$baseUrl/dashboard?id=$projectKey&$branchParam"
        val newIssuesLink = "$baseUrl/project/issues?inNewCodePeriod=true&id=$projectKey&$branchParam"

        if (!result.isQualityGatePassed) {
            messages.add(
                "##teamcity[buildProblem description='Sonar Quality Gate FAILED, details: $dashboardLink' identity='sonar-quality-gate']"
            )
            return messages
        }

        val isMaster = branch == "master"

        if (result.hasNewIssues) {
            if (isMaster && result.hasFailedMetrics) {
                val metrics = result.failedMetrics.joinToString(", ")
                messages.add(
                    "##teamcity[buildStatus text='Warning: ${result.newIssueCount} new SAST issues found and $metrics rating(s) below target - details: $dashboardLink']"
                )
            } else {
                messages.add(
                    "##teamcity[buildStatus text='Warning: ${result.newIssueCount} new SAST issues found - details: $newIssuesLink']"
                )
            }
        } else {
            if (isMaster && result.hasFailedMetrics) {
                val metrics = result.failedMetrics.joinToString(", ")
                messages.add(
                    "##teamcity[buildStatus text='Warning: SAST $metrics rating(s) below target - details: ${dashboardLink}&codeScope=overall']"
                )
            }
        }

        return messages
    }

    companion object {
        /**
         * Translates a branch identifier into the correct URL query parameter.
         * `"pull-request/123"` → `"pullRequest=123"`, otherwise `"branch=<name>"`.
         */
        fun branchOrPrParam(branch: String): String {
            return if (branch.contains("pull-request")) {
                "pullRequest=${branch.substringAfter("pull-request/")}"
            } else {
                "branch=$branch"
            }
        }
    }
}

