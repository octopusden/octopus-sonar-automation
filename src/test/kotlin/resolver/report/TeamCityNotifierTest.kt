package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.dto.QualityGateCheckResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamCityNotifierTest {

    // sourceBranch == targetBranch → production branch
    private val productionNotifier = TeamCityNotifier(
        sonarServerUrl = "https://sonar.example.com",
        projectKey = "proj",
        sourceBranch = "master",
        targetBranch = "master",
    )

    // sourceBranch != targetBranch → feature branch
    private val featureNotifier = TeamCityNotifier(
        sonarServerUrl = "https://sonar.example.com",
        projectKey = "proj",
        sourceBranch = "feature/abc",
        targetBranch = "master",
    )

    // ── Quality gate failed ──────────────────────────────────────────────────

    @Test
    fun `quality gate failed emits buildProblem`() {
        val result = QualityGateCheckResult("ERROR", newIssueCount = 3, failedMetrics = emptyList())

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildProblem"))
        assertTrue(messages[0].contains("Sonar Quality Gate FAILED"))
        assertTrue(messages[0].contains("dashboard?id=proj&branch=master"))
    }

    @Test
    fun `quality gate failed returns only buildProblem even with new issues and failed metrics`() {
        val result = QualityGateCheckResult("ERROR", newIssueCount = 10, failedMetrics = listOf("security"))

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildProblem"))
    }

    // ── Production branch (sourceBranch == targetBranch) ─────────────────────

    @Test
    fun `quality gate passed with new issues on production branch and failed metrics`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 5, failedMetrics = listOf("security", "reliability"))

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildStatus"))
        assertTrue(messages[0].contains("5 new SAST issues found"))
        assertTrue(messages[0].contains("security, reliability rating(s) below target"))
        assertTrue(messages[0].contains("dashboard?id=proj&branch=master"))
    }

    @Test
    fun `quality gate passed with new issues on production branch without failed metrics`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 3, failedMetrics = emptyList())

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("3 new SAST issues found"))
        assertTrue(messages[0].contains("project/issues?inNewCodePeriod=true"))
    }

    @Test
    fun `quality gate passed no new issues but failed metrics on production branch`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = listOf("maintainability"))

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("SAST maintainability rating(s) below target"))
        assertTrue(messages[0].contains("codeScope=overall"))
    }

    @Test
    fun `quality gate passed no new issues no failed metrics emits nothing`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = emptyList())

        val messages = productionNotifier.buildMessages(result)

        assertTrue(messages.isEmpty())
    }

    // ── Feature branch (sourceBranch != targetBranch) ────────────────────────

    @Test
    fun `quality gate passed with new issues on feature branch`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 2, failedMetrics = listOf("security"))

        val messages = featureNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("2 new SAST issues found"))
        // Non-production: no mention of failed metrics, links to new issues
        assertTrue(messages[0].contains("project/issues?inNewCodePeriod=true"))
        assertFalse(messages[0].contains("rating(s) below target"))
    }

    @Test
    fun `quality gate passed no issues but failed metrics on feature branch emits nothing`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = listOf("security"))

        val messages = featureNotifier.buildMessages(result)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `quality gate passed with new issues but no failed metrics on feature branch`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 7, failedMetrics = emptyList())

        val messages = featureNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("7 new SAST issues found"))
        assertTrue(messages[0].contains("project/issues?inNewCodePeriod=true"))
    }

    // ── Pull request branches ────────────────────────────────────────────────

    @Test
    fun `pull-request branch uses pullRequest param in links`() {
        val prNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "pull-requests/42", "master")
        val result = QualityGateCheckResult("OK", newIssueCount = 3, failedMetrics = emptyList())

        val messages = prNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("pullRequest=42"), "Should use pullRequest param instead of branch")
        assertFalse(messages[0].contains("branch="), "Should not use branch param for pull-request branches")
    }

    @Test
    fun `pull-request quality gate failure uses pullRequest param`() {
        val prNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "pull-requests/99", "master")
        val result = QualityGateCheckResult("ERROR", newIssueCount = 0, failedMetrics = emptyList())

        val messages = prNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("pullRequest=99"))
    }

    // ── branchOrPrParam companion function ───────────────────────────────────

    @Test
    fun `branchOrPrParam returns branch param for regular branch`() {
        assertEquals("branch=main", TeamCityNotifier.branchOrPrParam("main"))
    }

    @Test
    fun `branchOrPrParam returns branch param for feature branch`() {
        assertEquals("branch=feature%2Fxyz", TeamCityNotifier.branchOrPrParam("feature/xyz"))
    }

    @Test
    fun `branchOrPrParam returns pullRequest param for pull-requests branch`() {
        assertEquals("pullRequest=42", TeamCityNotifier.branchOrPrParam("pull-requests/42"))
    }

    // ── trailing slash on server URL ─────────────────────────────────────────

    @Test
    fun `trailing slash on sonar server URL is trimmed`() {
        val notifier = TeamCityNotifier("https://sonar.example.com/", "proj", "master", "master")
        val result = QualityGateCheckResult("ERROR", newIssueCount = 0, failedMetrics = emptyList())

        val messages = notifier.buildMessages(result)

        assertTrue(messages[0].contains("https://sonar.example.com/dashboard"))
        assertFalse(messages[0].contains("https://sonar.example.com//dashboard"))
    }

    // ── quality gate failed on feature branch ────────────────────────────────

    @Test
    fun `quality gate failed on feature branch still emits only buildProblem`() {
        val result = QualityGateCheckResult("ERROR", newIssueCount = 5, failedMetrics = listOf("security"))

        val messages = featureNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildProblem"))
        assertTrue(messages[0].contains("Sonar Quality Gate FAILED"))
        assertTrue(messages[0].contains("branch=feature%2Fabc"))
    }

    // ── multiple failed metrics formatting ───────────────────────────────────

    @Test
    fun `multiple failed metrics are comma-separated in production warning`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = listOf("reliability", "security", "maintainability"))

        val messages = productionNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("reliability, security, maintainability rating(s) below target"))
    }
}
