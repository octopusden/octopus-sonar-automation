package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.SonarClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.sonar.client.dto.HotspotDTO
import org.octopusden.octopus.sonar.client.dto.HotspotsResponseDTO
import org.octopusden.octopus.sonar.client.dto.IssueDTO
import org.octopusden.octopus.sonar.client.dto.IssueImpactDTO
import org.octopusden.octopus.sonar.client.dto.IssuesResponseDTO
import org.octopusden.octopus.sonar.client.dto.PagingDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateProjectStatusDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateResponseDTO
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportGeneratorTest {

    private val sonarClient = mockk<SonarClient>()

    private fun emptyPaging() = PagingDTO(pageIndex = 1, pageSize = 500, total = 0)

    private fun issueResponse(
        issues: List<IssueDTO> = emptyList(),
        effortTotal: Int = 0,
        total: Int = issues.size,
    ) = IssuesResponseDTO(
        paging = PagingDTO(pageIndex = 1, pageSize = 500, total = total),
        effortTotal = effortTotal,
        issues = issues,
    )

    private fun hotspotResponse(
        hotspots: List<HotspotDTO> = emptyList(),
        total: Int = hotspots.size,
    ) = HotspotsResponseDTO(
        paging = PagingDTO(pageIndex = 1, pageSize = 500, total = total),
        hotspots = hotspots,
    )

    private fun qualityGateResponse(status: String = "OK") = QualityGateResponseDTO(
        projectStatus = QualityGateProjectStatusDTO(status = status)
    )

    private fun sampleIssue(
        key: String = "AX1",
        severity: String = "MAJOR",
        message: String = "Fix this",
        rule: String = "java:S1234",
        type: String = "BUG",
        component: String = "proj:src/main/java/com/example/Foo.java",
        line: Int = 42,
        effort: String = "15min",
    ) = IssueDTO(
        key = key,
        rule = rule,
        severity = severity,
        component = component,
        line = line,
        status = "OPEN",
        message = message,
        effort = effort,
        author = "dev@example.com",
        creationDate = Date(),
        updateDate = Date(),
        type = type,
        scope = "MAIN",
        impacts = listOf(IssueImpactDTO(softwareQuality = "RELIABILITY", severity = "HIGH")),
    )

    private fun sampleHotspot(
        key: String = "HS1",
        message: String = "Review this hotspot",
        component: String = "proj:src/main/java/com/example/Bar.java",
    ) = HotspotDTO(
        key = key,
        component = component,
        project = "proj",
        securityCategory = "log-injection",
        vulnerabilityProbability = "HIGH",
        status = "TO_REVIEW",
        line = 10,
        message = message,
        author = "dev@example.com",
        creationDate = null,
        updateDate = null,
        ruleKey = "java:S5122",
    )

    @Test
    fun `generates no-issue report when no issues and no hotspots`() {
        every { sonarClient.searchIssues(any(), any(), any(), any(), any()) } returns issueResponse()
        every { sonarClient.searchHotspots(any(), any(), any(), any(), any()) } returns hotspotResponse()
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("OK")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "my-service",
                componentVersion = "1.0.0",
                sonarProjectName = "PS/my-service:module",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            assertTrue(file.exists())
            assertEquals("my-service-1.0.0-sast-report.html", file.name)

            val content = file.readText()
            assertTrue(content.contains("my-service:1.0.0"), "Should contain component name and version")
            assertTrue(content.contains("PS/my-service"), "Should contain repository")
            assertTrue(content.contains("Quality Gate Passed"), "Should show passed quality gate")
            assertTrue(content.contains("No new issues found"), "Should show no-issue clean state")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `generates report with issues and hotspots`() {
        val issues = listOf(sampleIssue())
        every { sonarClient.searchIssues(any(), any(), any(), 500, 1) } returns issueResponse(issues, effortTotal = 15)
        every { sonarClient.searchHotspots(any(), any(), any(), any(), any()) } returns hotspotResponse(listOf(sampleHotspot()))
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("OK")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "my-service",
                componentVersion = "1.0.0",
                sonarProjectName = "PS/my-service:module",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            assertTrue(file.exists())
            val content = file.readText()
            assertTrue(content.contains("Fix this"), "Should contain issue message")
            assertTrue(content.contains("Review this hotspot"), "Should contain hotspot message")
            assertTrue(content.contains("Foo.java"), "Should contain extracted file name")
            assertTrue(content.contains("Bar.java"), "Should contain hotspot file name")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `generates report with failed quality gate`() {
        every { sonarClient.searchIssues(any(), any(), any(), any(), any()) } returns issueResponse()
        every { sonarClient.searchHotspots(any(), any(), any(), any(), any()) } returns hotspotResponse()
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("ERROR")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "my-service",
                componentVersion = "2.0.0",
                sonarProjectName = "PS/my-service",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            val content = file.readText()
            assertTrue(content.contains("Quality Gate Failed"), "Should show failed quality gate")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `handles pagination for issues`() {
        // Page 1: 500 items, total = 600
        val page1Issues = (1..500).map { sampleIssue(key = "AX$it") }
        val page2Issues = (501..600).map { sampleIssue(key = "AX$it") }

        every { sonarClient.searchIssues(any(), any(), any(), 500, 1) } returns IssuesResponseDTO(
            paging = PagingDTO(pageIndex = 1, pageSize = 500, total = 600),
            effortTotal = 100,
            issues = page1Issues,
        )
        every { sonarClient.searchIssues(any(), any(), any(), 500, 2) } returns IssuesResponseDTO(
            paging = PagingDTO(pageIndex = 2, pageSize = 500, total = 600),
            effortTotal = 100,
            issues = page2Issues,
        )
        every { sonarClient.searchHotspots(any(), any(), any(), any(), any()) } returns hotspotResponse()
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("OK")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "big-project",
                componentVersion = "3.0.0",
                sonarProjectName = "PS/big-project",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            assertTrue(file.exists())
            verify(exactly = 1) { sonarClient.searchIssues(any(), any(), any(), 500, 1) }
            verify(exactly = 1) { sonarClient.searchIssues(any(), any(), any(), 500, 2) }
            // No truncation — total (600) is within the 10,000 limit.
            // Check the id= attribute, not the class name (which also appears in the <style> block).
            assertFalse(file.readText().contains("id=\"truncation-notice\""), "Should not show truncation notice for 600 issues")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `stops at 10000 result limit and shows truncation notice`() {
        val maxResults = ReportDataFetcher.SONAR_MAX_RESULTS   // 10_000
        val pageSize = 500
        val totalOnSonar = 12_000   // exceeds the limit
        val totalPages = maxResults / pageSize   // 20 pages → exactly 10,000 fetched

        // Mock all 20 pages
        for (page in 1..totalPages) {
            val pageIssues = ((page - 1) * pageSize + 1..page * pageSize)
                .map { sampleIssue(key = "AX$it") }
            every { sonarClient.searchIssues(any(), any(), any(), pageSize, page) } returns IssuesResponseDTO(
                paging = PagingDTO(pageIndex = page, pageSize = pageSize, total = totalOnSonar),
                effortTotal = page * 100,
                issues = pageIssues,
            )
        }
        // Page 21 must never be called — if it is, the mock throws an unexpected call error
        every { sonarClient.searchHotspots(any(), any(), any(), any(), any()) } returns hotspotResponse()
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("OK")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "big-project",
                componentVersion = "5.0.0",
                sonarProjectName = "PS/big-project",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            assertTrue(file.exists())

            // Page 21 must never have been requested
            verify(exactly = 0) { sonarClient.searchIssues(any(), any(), any(), pageSize, 21) }

            val content = file.readText()
            assertTrue(content.contains("id=\"truncation-notice\""), "Should show truncation notice")
            assertTrue(content.contains("10,000"), "Should mention the 10,000 limit")
            assertTrue(content.contains("12,000"), "Should show total issue count from SonarQube")
            assertTrue(content.contains("View all on SonarQube"), "Should contain link to SonarQube")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `stops hotspots at 10000 result limit and shows truncation notice`() {
        val maxResults = ReportDataFetcher.SONAR_MAX_RESULTS
        val pageSize = 500
        val totalOnSonar = 11_500
        val totalPages = maxResults / pageSize   // 20 pages

        every { sonarClient.searchIssues(any(), any(), any(), any(), any()) } returns issueResponse()

        for (page in 1..totalPages) {
            val pageHotspots = ((page - 1) * pageSize + 1..page * pageSize)
                .map { sampleHotspot(key = "HS$it") }
            every { sonarClient.searchHotspots(any(), any(), any(), pageSize, page) } returns HotspotsResponseDTO(
                paging = PagingDTO(pageIndex = page, pageSize = pageSize, total = totalOnSonar),
                hotspots = pageHotspots,
            )
        }
        every { sonarClient.getQualityGateStatus(any(), any()) } returns qualityGateResponse("OK")

        val outputDir = createTempDir("report-test")
        try {
            val generator = ReportGenerator(sonarClient)
            val file = generator.generate(
                projectKey = "proj",
                branch = "master",
                componentName = "hotspot-heavy",
                componentVersion = "1.0.0",
                sonarProjectName = "PS/hotspot-heavy",
                sonarServerUrl = "https://sonar.example.com",
                outputDir = outputDir,
            )

            assertTrue(file.exists())
            verify(exactly = 0) { sonarClient.searchHotspots(any(), any(), any(), pageSize, 21) }

            val content = file.readText()
            assertTrue(content.contains("id=\"truncation-notice\""), "Should show truncation notice for hotspots")
            assertTrue(content.contains("View all on SonarQube"), "Should contain link to SonarQube")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$prefix-${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }
}

