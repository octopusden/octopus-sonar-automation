package org.octopusden.octopus.sonar.resolver.report

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.sonar.client.SonarClient
import org.octopusden.octopus.sonar.client.dto.ComponentMeasuresDTO
import org.octopusden.octopus.sonar.client.dto.IssuesResponseDTO
import org.octopusden.octopus.sonar.client.dto.MeasureDTO
import org.octopusden.octopus.sonar.client.dto.MeasuresResponseDTO
import org.octopusden.octopus.sonar.client.dto.PagingDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateProjectStatusDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateResponseDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityGateCheckerTest {
    private val sonarClient = mockk<SonarClient>()
    private val checker = QualityGateChecker(sonarClient)

    private fun qualityGateResponse(status: String) =
        QualityGateResponseDTO(
            projectStatus = QualityGateProjectStatusDTO(status = status),
        )

    private fun newIssuesResponse(total: Int) =
        IssuesResponseDTO(
            paging = PagingDTO(pageIndex = 1, pageSize = 1, total = total),
            effortTotal = 0,
            issues = emptyList(),
        )

    private fun measuresResponse(vararg measures: MeasureDTO) =
        MeasuresResponseDTO(
            component =
                ComponentMeasuresDTO(
                    key = "proj",
                    name = "proj",
                    measures = measures.toList(),
                ),
        )

    @Test
    fun `quality gate passed with no issues and all ratings at best`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("OK")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns
            measuresResponse(
                MeasureDTO("software_quality_reliability_rating", "1.0", true),
                MeasureDTO("software_quality_security_rating", "1.0", true),
                MeasureDTO("software_quality_maintainability_rating", "1.0", true),
                MeasureDTO("security_review_rating", "1.0", true),
            )

        val result = checker.check("proj", "master")

        assertTrue(result.isQualityGatePassed)
        assertFalse(result.hasNewIssues)
        assertFalse(result.hasFailedMetrics)
        assertEquals(0, result.newIssueCount)
        assertEquals(emptyList(), result.failedMetrics)
    }

    @Test
    fun `quality gate failed`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("ERROR")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(5)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns measuresResponse()

        val result = checker.check("proj", "master")

        assertFalse(result.isQualityGatePassed)
        assertEquals("ERROR", result.qualityGateStatus)
    }

    @Test
    fun `detects new issues`() {
        every { sonarClient.getQualityGateStatus("feature", "proj") } returns qualityGateResponse("OK")
        every { sonarClient.searchIssues("proj", "feature", false, 1, 1, true) } returns newIssuesResponse(12)
        every { sonarClient.getMeasures("feature", "proj", any<String>()) } returns measuresResponse()

        val result = checker.check("proj", "feature")

        assertTrue(result.hasNewIssues)
        assertEquals(12, result.newIssueCount)
    }

    @Test
    fun `detects failed metrics`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("OK")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns
            measuresResponse(
                MeasureDTO("software_quality_reliability_rating", "1.0", true),
                MeasureDTO("software_quality_security_rating", "3.0", false),
                MeasureDTO("software_quality_maintainability_rating", "1.0", true),
                MeasureDTO("security_review_rating", "2.0", false),
            )

        val result = checker.check("proj", "master")

        assertTrue(result.hasFailedMetrics)
        assertEquals(listOf("security", "security hotspots"), result.failedMetrics)
    }

    @Test
    fun `all metrics at best value returns empty failed metrics`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("OK")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns
            measuresResponse(
                MeasureDTO("software_quality_reliability_rating", "1.0", true),
                MeasureDTO("software_quality_security_rating", "1.0", true),
                MeasureDTO("software_quality_maintainability_rating", "1.0", true),
                MeasureDTO("security_review_rating", "1.0", true),
            )

        val result = checker.check("proj", "master")

        assertEquals(emptyList(), result.failedMetrics)
    }

    @Test
    fun `no measures returned results in empty failed metrics`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("OK")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns measuresResponse()

        val result = checker.check("proj", "master")

        assertEquals(emptyList(), result.failedMetrics)
    }

    @Test
    fun `combined failed gate with new issues and failed metrics`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("ERROR")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(10)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns
            measuresResponse(
                MeasureDTO("software_quality_reliability_rating", "3.0", false),
            )

        val result = checker.check("proj", "master")

        assertFalse(result.isQualityGatePassed)
        assertTrue(result.hasNewIssues)
        assertTrue(result.hasFailedMetrics)
        assertEquals(listOf("reliability"), result.failedMetrics)
    }

    // --- Quality gate WARN and NONE (pending) retry tests ---

    @Test
    fun `WARN quality gate status is treated as passed`() {
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("WARN")
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns measuresResponse()

        val result = checker.check("proj", "master")

        assertTrue(result.isQualityGatePassed)
        assertEquals("WARN", result.qualityGateStatus)
    }

    @Test
    fun `NONE status retries until OK and returns OK`() {
        val fastChecker = QualityGateChecker(sonarClient, maxRetries = 3, retryDelaySeconds = 0)
        every { sonarClient.getQualityGateStatus("master", "proj") } returnsMany
            listOf(
                qualityGateResponse("NONE"),
                qualityGateResponse("NONE"),
                qualityGateResponse("OK"),
            )
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(0)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns measuresResponse()

        val result = fastChecker.check("proj", "master")

        assertTrue(result.isQualityGatePassed)
        assertEquals("OK", result.qualityGateStatus)
        verify(exactly = 3) { sonarClient.getQualityGateStatus("master", "proj") }
    }

    @Test
    fun `NONE status retries until ERROR and returns ERROR`() {
        val fastChecker = QualityGateChecker(sonarClient, maxRetries = 3, retryDelaySeconds = 0)
        every { sonarClient.getQualityGateStatus("master", "proj") } returnsMany
            listOf(
                qualityGateResponse("NONE"),
                qualityGateResponse("ERROR"),
            )
        every { sonarClient.searchIssues("proj", "master", false, 1, 1, true) } returns newIssuesResponse(5)
        every { sonarClient.getMeasures("master", "proj", any<String>()) } returns measuresResponse()

        val result = fastChecker.check("proj", "master")

        assertFalse(result.isQualityGatePassed)
        assertEquals("ERROR", result.qualityGateStatus)
        verify(exactly = 2) { sonarClient.getQualityGateStatus("master", "proj") }
    }

    @Test
    fun `NONE status exhausts retries and throws exception`() {
        val fastChecker = QualityGateChecker(sonarClient, maxRetries = 2, retryDelaySeconds = 0)
        every { sonarClient.getQualityGateStatus("master", "proj") } returns qualityGateResponse("NONE")

        val ex =
            assertFailsWith<IllegalStateException> {
                fastChecker.check("proj", "master")
            }

        assertTrue(ex.message!!.contains("NONE"))
        // Called maxRetries+1 times total (initial attempt + 2 retries)
        verify(exactly = 3) { sonarClient.getQualityGateStatus("master", "proj") }
    }
}
