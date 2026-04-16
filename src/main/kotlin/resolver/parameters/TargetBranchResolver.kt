package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.sonar.dto.CommitStampDTO
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import java.util.Date
import java.util.logging.Logger

/**
 * Resolves the Sonar target branch for a given regular-branch build.
 *
 * For **pull-request** builds the target branch is read directly from TeamCity parameters
 * (`teamcity.pullRequest.target.branch`) and this resolver is not involved.
 *
 * For **regular branch** builds it uses the VCS Facade to identify which candidate branch
 * the source branch diverged from, by comparing commit histories.
 */
class TargetBranchResolver(
    private val vcsFacadeClient: ClassicVcsFacadeClient,
    private val initialWindowDays: Int = 10,
    private val maxWindowDays: Int = 365,
    private val windowGrowthFactor: Int = 2,
    private val nowProviderMillis: () -> Long = { System.currentTimeMillis() },
) {
    init {
        require(initialWindowDays > 0) { "initialWindowDays must be > 0" }
        require(maxWindowDays >= initialWindowDays) { "maxWindowDays must be >= initialWindowDays" }
        require(windowGrowthFactor > 1) { "windowGrowthFactor must be > 1" }
    }

    /**
     * Finds the best-matching base branch by comparing the source branch's commit history
     * against each candidate branch.
     */
    fun findTargetBranch(commit: CommitStampDTO, candidates: List<String>): String {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }

        if (commit.branch in candidates) {
            logger.info("Source branch '${commit.branch}' is itself a candidate - returning it as target")
            return commit.branch
        }

        val windowDaysList = buildWindowDays()
        val sourceHashesByWindow = mutableMapOf<Int, List<String>>()

        candidateLoop@ for (candidate in candidates) {
            logger.fine("Evaluating candidate '$candidate'")

            for (windowDays in windowDaysList) {
                val sourceBranchHashes = runCatching {
                    sourceHashesByWindow.getOrPut(windowDays) {
                        val fromDate = Date(nowProviderMillis() - windowDays * DAY_IN_MILLIS)
                        logger.fine("Fetching source commits using last $windowDays days window")
                        vcsFacadeClient.getCommits(
                            commit.vcsUrl,
                            fromDate = fromDate,
                            toHashOrRef = commit.branch,
                            fromHashOrRef = null,
                        ).map { it.hash }
                    }
                }.getOrElse {
                    logger.warning("Failed to fetch commits for source branch '${commit.branch}': ${it.message}")
                    return candidates.first()
                }

                if (sourceBranchHashes.isEmpty()) {
                    logger.fine("No commits found on '${commit.branch}' in the last $windowDays days")
                    continue
                }

                val fromDate = Date(nowProviderMillis() - windowDays * DAY_IN_MILLIS)
                val candidateHashes = try {
                    vcsFacadeClient.getCommits(
                        commit.vcsUrl,
                        fromDate = fromDate,
                        toHashOrRef = candidate,
                        fromHashOrRef = null,
                    ).asSequence().map { it.hash }.toHashSet()
                } catch (_: NotFoundException) {
                    logger.warning("Candidate branch '$candidate' not found in VCS Facade - skipping")
                    continue@candidateLoop
                } catch (e: Exception) {
                    logger.warning("Failed to fetch commits for candidate '$candidate': ${e.message}")
                    continue@candidateLoop
                }

                val commonHash = sourceBranchHashes.firstOrNull { it in candidateHashes }
                if (commonHash != null) {
                    logger.info("'${commit.branch}' diverged from '$candidate' at commit $commonHash")
                    return candidate
                }
            }
        }

        logger.warning("Could not determine target branch from $candidates - falling back to '${candidates.first()}'")
        return candidates.first()
    }

    private fun buildWindowDays(): List<Int> {
        val windows = mutableListOf<Int>()
        var current = initialWindowDays

        while (current < maxWindowDays) {
            windows += current
            val next = current * windowGrowthFactor
            if (next <= current) break
            current = next
        }

        if (windows.isEmpty() || windows.last() != maxWindowDays) {
            windows += maxWindowDays
        }

        return windows
    }

    companion object {
        private val logger = Logger.getLogger(TargetBranchResolver::class.java.name)
        private const val DAY_IN_MILLIS = 24L * 60 * 60 * 1000
    }
}
