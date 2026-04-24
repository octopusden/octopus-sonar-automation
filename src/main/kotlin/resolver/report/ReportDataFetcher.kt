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
        private const val MAX_PAGES = 100
    }

    data class FetchedData(
        val effortTotal: Int,
        val issues: List<IssueDTO>,
        val hotspots: List<HotspotDTO>,
        val qualityGateStatus: String,
    )

    private data class IssuesFetchResult(
        val issues: List<IssueDTO>,
        val effortTotal: Int,
    )

    fun fetch(projectKey: String, branch: String): FetchedData {
        val issuesResult = fetchAllIssues(projectKey, branch)
        val hotspots = fetchAllHotspots(projectKey, branch)
        val qualityGateStatus = sonarClient.getQualityGateStatus(branch, projectKey).projectStatus.status

        return FetchedData(
            effortTotal = issuesResult.effortTotal,
            issues = issuesResult.issues,
            hotspots = hotspots,
            qualityGateStatus = qualityGateStatus,
        )
    }

    private fun fetchAllIssues(projectKey: String, branch: String): IssuesFetchResult {
        val allIssues = mutableListOf<IssueDTO>()
        var page = 1
        var effortTotal = 0

        while (page <= MAX_PAGES) {
            val response = sonarClient.searchIssues(projectKey, branch, resolved = false, ps = PAGE_SIZE, p = page)
            allIssues.addAll(response.issues)

            if (page == 1) {
                effortTotal = response.effortTotal ?: 0
            }

            val paging = response.paging
            if (paging.pageIndex * paging.pageSize >= paging.total) break
            page++
        }

        return IssuesFetchResult(issues = allIssues, effortTotal = effortTotal)
    }

    private fun fetchAllHotspots(projectKey: String, branch: String): List<HotspotDTO> {
        val allHotspots = mutableListOf<HotspotDTO>()
        var page = 1

        while (page <= MAX_PAGES) {
            val response = sonarClient.searchHotspots(projectKey, branch, status = "TO_REVIEW", ps = PAGE_SIZE, p = page)
            allHotspots.addAll(response.hotspots)

            val paging = response.paging
            if (paging.pageIndex * paging.pageSize >= paging.total) break
            page++
        }

        return allHotspots
    }
}
