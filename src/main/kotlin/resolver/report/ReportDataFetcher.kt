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
        /**
         * Backstop against runaway pagination. With PAGE_SIZE=500, [SONAR_MAX_RESULTS]
         * stops the loop at page 20, so this limit can only be reached if PAGE_SIZE is
         * reduced below 100. Keep it as a safety net, not as the primary guard.
         */
        private const val MAX_PAGES = 100

        /**
         * Hard ceiling imposed by SonarQube: the API returns HTTP 400 when the requested
         * offset exceeds 10,000.  We stop fetching once we would breach this limit.
         */
        const val SONAR_MAX_RESULTS = 10_000
    }

    data class FetchedData(
        val effortTotal: Int,
        val issues: List<IssueDTO>,
        val totalIssues: Int,
        val issuesTruncated: Boolean,
        val hotspots: List<HotspotDTO>,
        val totalHotspots: Int,
        val hotspotsTruncated: Boolean,
        val qualityGateStatus: String,
    )

    private data class IssuesFetchResult(
        val issues: List<IssueDTO>,
        val effortTotal: Int,
        val totalIssues: Int,
        val truncated: Boolean,
    )

    private data class HotspotsFetchResult(
        val hotspots: List<HotspotDTO>,
        val totalHotspots: Int,
        val truncated: Boolean,
    )

    fun fetch(projectKey: String, branch: String): FetchedData {
        val issuesResult = fetchAllIssues(projectKey, branch)
        val hotspotsResult = fetchAllHotspots(projectKey, branch)
        val qualityGateStatus = sonarClient.getQualityGateStatus(branch, projectKey).projectStatus.status

        return FetchedData(
            effortTotal = issuesResult.effortTotal,
            issues = issuesResult.issues,
            totalIssues = issuesResult.totalIssues,
            issuesTruncated = issuesResult.truncated,
            hotspots = hotspotsResult.hotspots,
            totalHotspots = hotspotsResult.totalHotspots,
            hotspotsTruncated = hotspotsResult.truncated,
            qualityGateStatus = qualityGateStatus,
        )
    }

    private fun fetchAllIssues(projectKey: String, branch: String): IssuesFetchResult {
        val allIssues = mutableListOf<IssueDTO>()
        var page = 1
        var effortTotal = 0
        var totalIssues = 0

        while (page <= MAX_PAGES) {
            val response = sonarClient.searchIssues(projectKey, branch, resolved = false, ps = PAGE_SIZE, p = page)
            allIssues.addAll(response.issues)

            if (page == 1) {
                effortTotal = response.effortTotal ?: 0
                totalIssues = response.paging.total
            }

            val paging = response.paging
            val fetched = paging.pageIndex * paging.pageSize

            if (fetched >= SONAR_MAX_RESULTS) {
                // effortTotal comes from the page-1 response and reflects the full
                // result set (e.g. 12,000 issues), not just the 10,000 we fetched.
                // This is intentional: the effort figure in the report is the total
                // remediation cost for the project, not a partial sum.
                return IssuesFetchResult(
                    issues = allIssues,
                    effortTotal = effortTotal,
                    totalIssues = totalIssues,
                    truncated = totalIssues > SONAR_MAX_RESULTS,
                )
            }

            if (fetched >= paging.total) break
            page++
        }

        return IssuesFetchResult(issues = allIssues, effortTotal = effortTotal, totalIssues = totalIssues, truncated = false)
    }

    private fun fetchAllHotspots(projectKey: String, branch: String): HotspotsFetchResult {
        val allHotspots = mutableListOf<HotspotDTO>()
        var page = 1
        var totalHotspots = 0

        while (page <= MAX_PAGES) {
            val response = sonarClient.searchHotspots(projectKey, branch, status = "TO_REVIEW", ps = PAGE_SIZE, p = page)
            allHotspots.addAll(response.hotspots)

            if (page == 1) {
                totalHotspots = response.paging.total
            }

            val paging = response.paging
            val fetched = paging.pageIndex * paging.pageSize

            if (fetched >= SONAR_MAX_RESULTS) {
                return HotspotsFetchResult(
                    hotspots = allHotspots,
                    totalHotspots = totalHotspots,
                    truncated = totalHotspots > SONAR_MAX_RESULTS,
                )
            }

            if (fetched >= paging.total) break
            page++
        }

        return HotspotsFetchResult(hotspots = allHotspots, totalHotspots = totalHotspots, truncated = false)
    }
}
