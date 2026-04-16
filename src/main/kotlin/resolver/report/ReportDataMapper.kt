package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.dto.HotspotDTO
import org.octopusden.octopus.sonar.client.dto.IssueDTO
import org.octopusden.octopus.sonar.dto.ReportData
import org.octopusden.octopus.sonar.dto.ReportHotspotItem
import org.octopusden.octopus.sonar.dto.ReportIssueItem

/**
 * Maps SonarQube API DTOs into report-friendly data structures.
 */
class ReportDataMapper {

    fun map(
        fetchedData: ReportDataFetcher.FetchedData,
        componentName: String,
        componentVersion: String,
        sonarProjectName: String,
        sonarServerUrl: String,
        sonarProjectKey: String,
        sourceBranch: String,
    ): ReportData {
        return ReportData(
            componentName = componentName,
            componentVersion = componentVersion,
            repository = extractRepository(sonarProjectName),
            qualityGateStatus = fetchedData.qualityGateStatus,
            effortTotal = formatEffortTotal(fetchedData.effortTotal),
            issues = fetchedData.issues.map { mapIssue(it) },
            hotspots = fetchedData.hotspots.map { mapHotspot(it) },
            sonarServerUrl = sonarServerUrl.trimEnd('/'),
            sonarProjectKey = sonarProjectKey,
            sourceBranch = sourceBranch,
        )
    }

    private fun mapIssue(issue: IssueDTO): ReportIssueItem {
        val impact = issue.impacts.first()
        return ReportIssueItem(
            severity = impact.severity,
            message = issue.message,
            rule = issue.rule,
            type = issue.type,
            softwareQuality = impact.softwareQuality,
            fileName = extractFileName(issue.component),
            line = issue.line,
            effort = formatEffort(issue.effort),
            component = issue.component,
            key = issue.key,
        )
    }

    private fun mapHotspot(hotspot: HotspotDTO): ReportHotspotItem {
        return ReportHotspotItem(
            message = hotspot.message,
            rule = hotspot.ruleKey ?: "",
            vulnerabilityProbability = hotspot.vulnerabilityProbability ?: "MEDIUM",
            securityCategory = hotspot.securityCategory,
            fileName = extractFileName(hotspot.component),
            line = hotspot.line ?: 0,
            component = hotspot.component,
            key = hotspot.key,
        )
    }

    companion object {
        /**
         * Extracts the repository part from the SONAR_PROJECT_NAME.
         * Format: "PROJECT/repo:(component)" → "PROJECT/repo"
         * If no colon is found, returns the full name.
         */
        fun extractRepository(sonarProjectName: String): String {
            val colonIndex = sonarProjectName.indexOf(':')
            return if (colonIndex >= 0) sonarProjectName.substring(0, colonIndex) else sonarProjectName
        }

        /**
         * Extracts the file name from the component path.
         * Format: "project-key:src/main/java/com/example/File.java" → "File.java"
         * Takes the part after the last colon, then the part after the last '/'.
         */
        fun extractFileName(component: String): String {
            val afterColon = if (component.contains(':')) {
                component.substringAfterLast(':')
            } else {
                component
            }
            return afterColon.substringAfterLast('/')
        }

        /**
         * Formats effort string by inserting space between number and unit.
         * Examples: "1min" → "1 min", "30min" → "30 min", "2h" → "2 h"
         */
        fun formatEffort(effort: String): String {
            return effort.replace(Regex("(\\d+)([a-zA-Z]+)"), "$1 $2")
        }

        /**
         * Formats effort total (in minutes) into a human-readable string.
         * Examples: 10 → "10 min", 90 → "1 h 30 min", 0 → "0 min"
         */
        fun formatEffortTotal(effortMinutes: Int): String {
            if (effortMinutes <= 0) return "0 min"
            val hours = effortMinutes / 60
            val minutes = effortMinutes % 60
            return buildString {
                if (hours > 0) append("$hours h")
                if (hours > 0 && minutes > 0) append(" ")
                if (minutes > 0) append("$minutes min")
            }
        }
    }
}

