package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.dto.ReportData
import org.octopusden.octopus.sonar.dto.ReportHotspotItem
import org.octopusden.octopus.sonar.dto.ReportIssueItem
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.tools.generic.EscapeTool
import java.io.StringWriter
import java.net.URLEncoder

/**
 * Renders the SAST report HTML from [ReportData] using Apache Velocity templates.
 *
 * Templates:
 * - `templates/sast-report.vm` — report with issues/hotspots
 * - `templates/sast-no-issue-report.vm` — clean report when no issues found
 */
class ReportHtmlRenderer {

    companion object {
        private const val TEMPLATE_REPORT = "templates/sast-report.vm"
        private const val TEMPLATE_NO_ISSUE = "templates/sast-no-issue-report.vm"

        private val SEVERITY_ORDER = listOf("BLOCKER", "HIGH", "MEDIUM", "LOW", "INFO")
    }

    private val engine: VelocityEngine = VelocityEngine().apply {
        setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath")
        setProperty("resource.loader.classpath.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
        setProperty(RuntimeConstants.ENCODING_DEFAULT, "UTF-8")
        setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8")
        init()
    }

    fun render(data: ReportData): String {
        val hasIssues = data.issues.isNotEmpty() || data.hotspots.isNotEmpty()
        val templatePath = if (hasIssues) TEMPLATE_REPORT else TEMPLATE_NO_ISSUE

        val context = VelocityContext()

        // Shared variables
        context.put("esc", EscapeTool())
        context.put("helper", TemplateHelper(data))
        context.put("componentName", data.componentName)
        context.put("componentVersion", data.componentVersion)
        context.put("repository", data.repository)
        context.put("effortTotal", data.effortTotal)

        // Quality gate
        val (cssClass, label) = qualityGateDisplay(data.qualityGateStatus)
        context.put("qualityGateCssClass", cssClass)
        context.put("qualityGateLabel", label)

        if (hasIssues) {
            // Summary counts
            val severityCounts = data.issues.groupBy { it.severity.uppercase() }.mapValues { it.value.size }
            context.put("totalIssueCount", data.issues.size + data.hotspots.size)
            context.put("blockerCount", severityCounts["BLOCKER"] ?: 0)
            context.put("highCount", severityCounts["HIGH"] ?: 0)
            context.put("mediumCount", severityCounts["MEDIUM"] ?: 0)
            context.put("lowCount", severityCounts["LOW"] ?: 0)
            context.put("infoCount", severityCounts["INFO"] ?: 0)
            context.put("hotspotCount", data.hotspots.size)

            // Issue groups (only groups that have issues)
            val issuesByGroup = data.issues.groupBy { it.severity.uppercase() }
            val severityGroups = SEVERITY_ORDER
                .filter { issuesByGroup.containsKey(it) }
                .map { sev ->
                    SeverityGroup(
                        key = sev.lowercase(),
                        name = sev.lowercase().replaceFirstChar { it.uppercase() },
                        issues = issuesByGroup[sev]!!,
                    )
                }
            context.put("severityGroups", severityGroups)

            // Hotspots
            context.put("hotspots", data.hotspots)
        }

        val writer = StringWriter()
        val template = engine.getTemplate(templatePath, "UTF-8")
        template.merge(context, writer)
        return writer.toString()
    }

    private fun qualityGateDisplay(status: String): Pair<String, String> = when (status.uppercase()) {
        "OK" -> "passed" to "Quality Gate Passed"
        else -> "failed" to "Quality Gate Failed"
    }

    /**
     * Data class representing a severity group for the template.
     */
    data class SeverityGroup(
        val key: String,
        val name: String,
        val issues: List<ReportIssueItem>,
    )

    /**
     * Helper object exposed to the Velocity template for formatting and URL generation.
     */
    class TemplateHelper(private val data: ReportData) {

        fun capitalise(s: String): String =
            s.lowercase().replaceFirstChar { it.uppercase() }

        fun formatType(type: String): String = when (type) {
            "CODE_SMELL" -> "Code Smell - Maintainability"
            "BUG" -> "Bug - Reliability"
            "VULNERABILITY" -> "Vulnerability - Security"
            "SECURITY_HOTSPOT" -> "Security Hotspot"
            else -> type
        }

        fun formatSecurityCategory(category: String): String =
            category.replace("_", " ")
                .lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        fun ruleUrl(ruleKey: String): String {
            val baseUrl = data.sonarServerUrl.trimEnd('/')
            val encoded = enc(ruleKey)
            return "$baseUrl/coding_rules?rule_key=$encoded&open=$encoded"
        }

        fun issueUrl(issue: ReportIssueItem): String {
            val baseUrl = data.sonarServerUrl.trimEnd('/')
            val projectKey = enc(data.sonarProjectKey)
            val issueKey = enc(issue.key)
            return "$baseUrl/project/issues?id=$projectKey&issues=$issueKey&open=$issueKey"
        }

        fun hotspotUrl(hotspot: ReportHotspotItem): String {
            val baseUrl = data.sonarServerUrl.trimEnd('/')
            val projectKey = enc(data.sonarProjectKey)
            val hotspotKey = enc(hotspot.key)
            return "$baseUrl/security_hotspots?id=$projectKey&hotspots=$hotspotKey"
        }

        private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}
