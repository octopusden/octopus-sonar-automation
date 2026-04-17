package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.SonarClient
import org.octopusden.octopus.sonar.dto.QualityGateCheckResult

/**
 * Checks the SonarQube quality gate status, new issue count, and metric ratings
 * for a given project and branch.
 */
class QualityGateChecker(private val sonarClient: SonarClient) {

    companion object {
        private val RATING_METRICS = listOf(
            "software_quality_reliability_rating",
            "software_quality_security_rating",
            "software_quality_maintainability_rating",
            "security_review_rating",
        )

        private val METRIC_DISPLAY_NAMES = mapOf(
            "software_quality_reliability_rating" to "reliability",
            "software_quality_security_rating" to "security",
            "software_quality_maintainability_rating" to "maintainability",
            "security_review_rating" to "security hotspots",
        )
    }

    /**
     * Performs quality gate status, new issue count, and failed metrics checks against SonarQube.
     */
    fun check(projectKey: String, branch: String): QualityGateCheckResult {
        val qualityGateStatus = fetchQualityGateStatus(projectKey, branch)
        val newIssueCount = fetchNewIssueCount(projectKey, branch)
        val failedMetrics = fetchFailedMetrics(projectKey, branch)

        return QualityGateCheckResult(
            qualityGateStatus = qualityGateStatus,
            newIssueCount = newIssueCount,
            failedMetrics = failedMetrics,
        )
    }

    private fun fetchQualityGateStatus(projectKey: String, branch: String): String {
        return sonarClient.getQualityGateStatus(branch, projectKey).projectStatus.status
    }

    private fun fetchNewIssueCount(projectKey: String, branch: String): Int {
        val response = sonarClient.searchIssues(
            componentKeys = projectKey,
            branch = branch,
            resolved = false,
            ps = 1,
            p = 1,
            inNewCodePeriod = true,
        )
        return response.paging.total
    }

    private fun fetchFailedMetrics(projectKey: String, branch: String): List<String> {
        val metricKeys = RATING_METRICS.joinToString(",")
        val response = sonarClient.getMeasures(branch, projectKey, metricKeys)

        return response.component.measures
            .filter { it.bestValue == false }
            .mapNotNull { METRIC_DISPLAY_NAMES[it.metric] }
    }
}

