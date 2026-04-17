package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.dto.QualityGateCheckResult
import org.octopusden.octopus.sonar.util.TeamCityEscaper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    private val sourceBranch: String,
    private val targetBranch: String,
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
        val branchParam = branchOrPrParam(sourceBranch)
        val encodedProjectKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8)
        val dashboardLink = "$baseUrl/dashboard?id=$encodedProjectKey&$branchParam"
        val newIssuesLink = "$baseUrl/project/issues?inNewCodePeriod=true&id=$encodedProjectKey&$branchParam"

        if (!result.isQualityGatePassed) {
            messages.add(
                "##teamcity[buildProblem description='${TeamCityEscaper.escape("Sonar Quality Gate FAILED, details: $dashboardLink")}' identity='sonar-quality-gate']"
            )
            return messages
        }

        val isProductionBranch = sourceBranch == targetBranch

        if (result.hasNewIssues) {
            if (isProductionBranch && result.hasFailedMetrics) {
                val metrics = result.failedMetrics.joinToString(", ")
                messages.add(
                    "##teamcity[buildStatus text='${TeamCityEscaper.escape("Warning: ${result.newIssueCount} new SAST issues found and $metrics rating(s) below target - details: $dashboardLink")}']"
                )
            } else {
                messages.add(
                    "##teamcity[buildStatus text='${TeamCityEscaper.escape("Warning: ${result.newIssueCount} new SAST issues found - details: $newIssuesLink")}']"
                )
            }
        } else {
            if (isProductionBranch && result.hasFailedMetrics) {
                val metrics = result.failedMetrics.joinToString(", ")
                messages.add(
                    "##teamcity[buildStatus text='${TeamCityEscaper.escape("Warning: SAST $metrics rating(s) below target - details: ${dashboardLink}&codeScope=overall")}']"
                )
            }
        }

        return messages
    }

    companion object {
        /**
         * Translates a branch identifier into the correct URL query parameter.
         * `"pull-requests/123"` → `"pullRequest=123"`, otherwise `"branch=<name>"`.
         */
        fun branchOrPrParam(branch: String): String {
            return if (branch.startsWith("pull-requests/")) {
                "pullRequest=${branch.removePrefix("pull-requests/")}"
            } else {
                "branch=$branch"
            }
        }
    }
}
