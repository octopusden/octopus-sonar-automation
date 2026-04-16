package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.dto.QualityGateCheckResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamCityNotifierTest {

    private val notifier = TeamCityNotifier(
        sonarServerUrl = "https://sonar.example.com",
        projectKey = "proj",
        branch = "master",
    )

    @Test
    fun `quality gate failed emits buildProblem`() {
        val result = QualityGateCheckResult("ERROR", newIssueCount = 3, failedMetrics = emptyList())

        val messages = notifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildProblem"))
        assertTrue(messages[0].contains("Sonar Quality Gate FAILED"))
        assertTrue(messages[0].contains("dashboard?id=proj&branch=master"))
    }

    @Test
    fun `quality gate passed with new issues on master and failed metrics`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 5, failedMetrics = listOf("security", "reliability"))

        val messages = notifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("##teamcity[buildStatus"))
        assertTrue(messages[0].contains("5 new SAST issues found"))
        assertTrue(messages[0].contains("security, reliability rating(s) below target"))
        assertTrue(messages[0].contains("dashboard?id=proj&branch=master"))
    }

    @Test
    fun `quality gate passed with new issues on master without failed metrics`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 3, failedMetrics = emptyList())

        val messages = notifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("3 new SAST issues found"))
        assertTrue(messages[0].contains("project/issues?inNewCodePeriod=true"))
    }

    @Test
    fun `quality gate passed with new issues on feature branch`() {
        val featureNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "feature/abc")
        val result = QualityGateCheckResult("OK", newIssueCount = 2, failedMetrics = listOf("security"))

        val messages = featureNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("2 new SAST issues found"))
        // Non-master: no mention of failed metrics, links to new issues
        assertTrue(messages[0].contains("project/issues?inNewCodePeriod=true"))
    }

    @Test
    fun `quality gate passed no new issues but failed metrics on master`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = listOf("maintainability"))

        val messages = notifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("SAST maintainability rating(s) below target"))
        assertTrue(messages[0].contains("codeScope=overall"))
    }

    @Test
    fun `quality gate passed no new issues no failed metrics emits nothing`() {
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = emptyList())

        val messages = notifier.buildMessages(result)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `quality gate passed no issues but failed metrics on non-master emits nothing`() {
        val featureNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "feature/xyz")
        val result = QualityGateCheckResult("OK", newIssueCount = 0, failedMetrics = listOf("security"))

        val messages = featureNotifier.buildMessages(result)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `pull-request branch uses pullRequest param in links`() {
        val prNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "pull-request/42")
        val result = QualityGateCheckResult("OK", newIssueCount = 3, failedMetrics = emptyList())

        val messages = prNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("pullRequest=42"), "Should use pullRequest param instead of branch")
        assertFalse(messages[0].contains("branch=pull-request"), "Should not contain raw branch param")
    }

    @Test
    fun `pull-request quality gate failure uses pullRequest param`() {
        val prNotifier = TeamCityNotifier("https://sonar.example.com", "proj", "pull-request/99")
        val result = QualityGateCheckResult("ERROR", newIssueCount = 0, failedMetrics = emptyList())

        val messages = prNotifier.buildMessages(result)

        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("pullRequest=99"))
    }
}

