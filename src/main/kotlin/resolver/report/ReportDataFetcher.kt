package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.SonarClient
import org.octopusden.octopus.sonar.client.dto.HotspotDTO
import org.octopusden.octopus.sonar.client.dto.IssueDTO

/**
 * Fetches all issues, hotspots, and quality gate status from SonarQube
 */
class ReportDataFetcher(private val sonarClient: SonarClient) {

    companion object {
        private const val PAGE_SIZE = 500
    }

    data class FetchedData(
        val effortTotal: Int,
        val issues: List<IssueDTO>,
        val hotspots: List<HotspotDTO>,
        val qualityGateStatus: String,
    )

    fun fetch(projectKey: String, branch: String): FetchedData {
        val issues = fetchAllIssues(projectKey, branch)
        val hotspots = fetchAllHotspots(projectKey, branch)
        val qualityGateStatus = sonarClient.getQualityGateStatus(branch, projectKey).projectStatus.status

        val effortTotal = if (issues.isNotEmpty()) {
            sonarClient.searchIssues(projectKey, branch, resolved = false, ps = 1, p = 1).effortTotal ?: 0
        } else {
            0
        }

        return FetchedData(
            effortTotal = effortTotal,
            issues = issues,
            hotspots = hotspots,
            qualityGateStatus = qualityGateStatus,
        )
    }

    private fun fetchAllIssues(projectKey: String, branch: String): List<IssueDTO> {
        val allIssues = mutableListOf<IssueDTO>()
        var page = 1

        while (true) {
            val response = sonarClient.searchIssues(projectKey, branch, resolved = false, ps = PAGE_SIZE, p = page)
            allIssues.addAll(response.issues)

            val paging = response.paging
            if (paging.pageIndex * paging.pageSize >= paging.total) break
            page++
        }

        return allIssues
    }

    private fun fetchAllHotspots(projectKey: String, branch: String): List<HotspotDTO> {
        val allHotspots = mutableListOf<HotspotDTO>()
        var page = 1

        while (true) {
            val response = sonarClient.searchHotspots(projectKey, branch, status = "TO_REVIEW", ps = PAGE_SIZE, p = page)
            allHotspots.addAll(response.hotspots)

            val paging = response.paging
            if (paging.pageIndex * paging.pageSize >= paging.total) break
            page++
        }

        return allHotspots
    }
}

