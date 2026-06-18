package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.SonarClient
import org.octopusden.octopus.sonar.dto.QualityGateCheckResult
import org.slf4j.LoggerFactory

/**
 * Checks the SonarQube quality gate status, new issue count, and metric ratings
 * for a given project and branch.
 *
 * SonarQube may return [PENDING_STATUS] ("NONE") while the quality gate is still being
 * computed after an analysis completes. The checker retries up to [maxRetries] times,
 * waiting [retryDelaySeconds] seconds between attempts. If the status is still pending
 * after all retries, an exception is thrown so the build can be re-triggered.
 *
 * Definitive statuses:
 *  - "OK"    → passed (gate met)
 *  - "WARN"  → passed (gate met with warnings, treated as pass per SonarQube convention)
 *  - "ERROR" → failed (gate not met)
 */
class QualityGateChecker(
    private val sonarClient: SonarClient,
    private val maxRetries: Int = 20,
    private val retryDelaySeconds: Int = 5,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(QualityGateChecker::class.java)

        private const val PENDING_STATUS = "NONE"

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
        repeat(maxRetries + 1) { attempt ->
            val status = sonarClient.getQualityGateStatus(branch, projectKey).projectStatus.status
            if (status != PENDING_STATUS) return status
            if (attempt < maxRetries) {
                logger.warn(
                    "Quality gate for '$projectKey' on '$branch' returned $PENDING_STATUS " +
                    "(not yet computed) — retrying in ${retryDelaySeconds}s " +
                    "(attempt ${attempt + 1}/$maxRetries)"
                )
                Thread.sleep(retryDelaySeconds * 1_000L)
            }
        }
        throw IllegalStateException(
            "Quality gate for '$projectKey' on '$branch' remained $PENDING_STATUS after " +
            "$maxRetries retries. Please ensure your build has Sonar analysis step enabled."
        )
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

