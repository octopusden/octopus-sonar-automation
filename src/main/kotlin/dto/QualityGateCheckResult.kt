package org.octopusden.octopus.sonar.dto

/**
 * Result of the quality gate and new-issue check against SonarQube.
 */
data class QualityGateCheckResult(
    val qualityGateStatus: String,
    val newIssueCount: Int,
    val failedMetrics: List<String>,
) {
    val isQualityGatePassed: Boolean get() = qualityGateStatus == "OK"
    val hasNewIssues: Boolean get() = newIssueCount > 0
    val hasFailedMetrics: Boolean get() = failedMetrics.isNotEmpty()
}

