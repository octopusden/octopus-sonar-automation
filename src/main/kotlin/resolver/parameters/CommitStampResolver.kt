package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.sonar.dto.CommitStampDTO
import org.octopusden.octopus.sonar.dto.ResolvedVCSDTO
import org.octopusden.octopus.sonar.util.BitbucketSshUrlParser
import org.octopusden.octopus.sonar.util.BranchConstants.DEFAULT_BRANCH_CANDIDATES
import org.octopusden.octopus.sonar.util.normalizedBranch
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.VersionControlSystemRootDTO
import org.slf4j.LoggerFactory

/**
 * Resolves the VCS context for the current TeamCity build by matching the build's revision list
 * against the component's VCS settings from the Components Registry Service
 */
class CommitStampResolver(
    private val teamcityClient: TeamcityRestClient,
    private val crsClient: ComponentsRegistryServiceClient,
) {
    /**
     * Resolves the VCS context for the given build.
     *
     * 1. Extracts all [CommitStampDTO]s from the TeamCity build's revision list
     * 2. Fetches the component's VCS settings from the Components Registry
     * 3. If the registry reports `NOT_AVAILABLE` or contains no roots, falls back to
     *    [resolveWithoutVcsSettings]; otherwise delegates to [resolveWithVcsSettings]
     */
    fun resolve(
        componentName: String,
        componentVersion: String,
        teamcityBuildId: Int
    ): ResolvedVCSDTO {
        val commitStamps = extractCommitStamps(teamcityBuildId)
        require(commitStamps.isNotEmpty()) { "commitStamps must not be empty" }

        val vcsSettings = crsClient.getVCSSetting(componentName, componentVersion)

        return if (vcsSettings.externalRegistry == NOT_AVAILABLE_EXTERNAL_REGISTRY || vcsSettings.versionControlSystemRoots.isEmpty()) {
            resolveWithoutVcsSettings(commitStamps)
        } else {
            resolveWithVcsSettings(commitStamps, vcsSettings.versionControlSystemRoots)
        }
    }

    /**
     * Extracts all commit stamps from the specified TeamCity build
     */
    private fun extractCommitStamps(teamcityBuildId: Int): List<CommitStampDTO> {
        val response = teamcityClient.getBuildById(teamcityBuildId)
        val revisions = response["revisions"] as? Map<String, Any> ?: emptyMap()
        val vcsRootCache = mutableMapOf<Int, Map<String, Any>>()

        return (revisions["revision"] as? List<Map<String, Any>> ?: emptyList())
            .mapNotNull { entry ->
                parseRevision(entry) { id ->
                        vcsRootCache.getOrPut(id) { teamcityClient.getVcsRootInstance(id) }
                    }
            }
    }

    /**
     * Parses a single revision entry from the TeamCity build response into a [CommitStampDTO]
     */
    private fun parseRevision(
        entry: Map<String, Any>,
        getVcsRoot: (Int) -> Map<String, Any>
    ): CommitStampDTO? {
        val cid    = entry["version"]      as? String ?: return null
        val branch = entry["vcsBranchName"] as? String ?: return null

        val vcsRootInstanceId = (entry["vcs-root-instance"] as? Map<*, *>)
            ?.get("id")?.toString()?.toIntOrNull()
            ?: return null

        val vcsRoot = getVcsRoot(vcsRootInstanceId)

        if (vcsRoot["vcsName"] == VCS_NAME_CVS) return null

        val propertyList = ((vcsRoot["properties"] as? Map<*, *>)
            ?.get("property") as? List<Map<String, String>>)
            ?: emptyList()

        val vcsUrl = propertyList
            .firstOrNull { it["name"] in VCS_URL_PROPERTY_NAMES }
            ?.get("value")
            ?: error("No VCS URL property found in vcs-root-instance $vcsRootInstanceId. " +
                    "Looked for: $VCS_URL_PROPERTY_NAMES")

        return CommitStampDTO(cid, branch.normalizedBranch(), vcsUrl)
    }

    /**
     * Resolves VCS context when the Components Registry does not provide VCS settings
     * (i.e. `externalRegistry` is `NOT_AVAILABLE` or the root list is empty)
     */
    private fun resolveWithoutVcsSettings(commitStamps: List<CommitStampDTO>): ResolvedVCSDTO {
        if (commitStamps.size > 1) {
            logger.warn(
                "externalRegistry is $NOT_AVAILABLE_EXTERNAL_REGISTRY but ${commitStamps.size} commit stamps found — using the first one."
            )
        }
        val commit = commitStamps.first()
        val (projectKey, repoKey) = BitbucketSshUrlParser.parseRepository(commit.vcsUrl)
        return ResolvedVCSDTO(
            commit = commit,
            defaultBranches = DEFAULT_BRANCH_CANDIDATES,
            bbProjectKey = projectKey,
            bbRepositoryKey = repoKey
        )
    }

    /**
     * Resolves VCS context by matching commit stamps against the component's declared VCS roots.
     * Finds the first commit stamp whose [CommitStampDTO.vcsUrl] matches a root's `vcsPath`.
     * The matching root's `branch` field (pipe-separated) is used as the candidate target-branch list.
     */
    private fun resolveWithVcsSettings(
        commitStamps: List<CommitStampDTO>,
        roots: List<VersionControlSystemRootDTO>
    ): ResolvedVCSDTO {
        val rootsByPath = roots.associateBy { it.vcsPath }
        val (matchedCommit, matchedRoot) = commitStamps.firstNotNullOfOrNull { stamp ->
            rootsByPath[stamp.vcsUrl]?.let { root -> stamp to root }
        } ?: error(
            "None of the commit stamps matched VCS roots ${rootsByPath.keys}. " +
            "Commit stamp URLs: ${commitStamps.map { it.vcsUrl }}"
        )

        val branches = matchedRoot.branch
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { null }
            ?: DEFAULT_BRANCH_CANDIDATES

        val (projectKey, repoKey) = BitbucketSshUrlParser.parseRepository(matchedCommit.vcsUrl)
        return ResolvedVCSDTO(
            commit = matchedCommit,
            defaultBranches = branches,
            bbProjectKey = projectKey,
            bbRepositoryKey = repoKey
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommitStampResolver::class.java)

        private const val VCS_NAME_CVS = "cvs"
        private const val NOT_AVAILABLE_EXTERNAL_REGISTRY = "NOT_AVAILABLE"

        private val VCS_URL_PROPERTY_NAMES = setOf("url", "repositoryPath", "cvs-root")
    }
}
