package org.octopusden.octopus.sonar.dto

/**
 * Represents a single issue item for the SAST report.
 */
data class ReportIssueItem(
    val severity: String,
    val message: String,
    val rule: String,
    val type: String,
    val softwareQuality: String,
    val fileName: String,
    val line: Int,
    val effort: String,
    val component: String,
    val key: String,
)

/**
 * Represents a single hotspot item for the SAST report.
 */
data class ReportHotspotItem(
    val message: String,
    val rule: String,
    val vulnerabilityProbability: String,
    val securityCategory: String,
    val fileName: String,
    val line: Int,
    val component: String,
    val key: String,
)

/**
 * Aggregated data required to render the SAST report.
 */
data class ReportData(
    val componentName: String,
    val componentVersion: String,
    val repository: String,
    val qualityGateStatus: String,
    val effortTotal: String,
    val issues: List<ReportIssueItem>,
    val hotspots: List<ReportHotspotItem>,
    val sonarServerUrl: String,
    val sonarProjectKey: String,
    val sourceBranch: String,
)

