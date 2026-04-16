package org.octopusden.octopus.sonar.resolver.parameters

import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.sonar.dto.ResolvedVCSDTO
import org.octopusden.octopus.sonar.dto.SonarParametersDTO
import org.octopusden.octopus.sonar.util.SonarParameterBuilder
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import java.nio.file.Path

/**
 * Thin orchestrator: wires the individual resolvers together and produces
 * a [org.octopusden.octopus.sonar.dto.SonarParametersDTO] for the current TeamCity build.
 */
class SonarParametersCalculator(
    private val teamcityClient: TeamcityRestClient,
    private val crsClient: ClassicComponentsRegistryServiceClient,
    private val vcsFacadeClient: ClassicVcsFacadeClient,

    private val componentName: String,
    private val componentVersion: String,
    private val teamcityBuildId: Int,
    private val sonarConfigDir: Path,

    private val commitStampResolver: CommitStampResolver = CommitStampResolver(teamcityClient, crsClient),
    private val targetBranchResolver: TargetBranchResolver = TargetBranchResolver(vcsFacadeClient),
    private val sonarServerResolver: SonarServerResolver = SonarServerResolver(crsClient),
    private val sonarExecutionResolver: SonarExecutionResolver = SonarExecutionResolver(crsClient, sonarConfigDir),
) {

    /**
     * Computes all Sonar parameters for the current build.
     *
     * Build mode is selected by checking whether the resolved branch contains
     * `pull-requests/`.
     *
     * When applied-SAST override exists for the component, project key/name come
     * from that override and branch-comparison Sonar parameters are intentionally
     * left empty.
     *
     * @return A fully populated [org.octopusden.octopus.sonar.dto.SonarParametersDTO].
     */
    fun calculate(): SonarParametersDTO {
        val resolvedVcs = commitStampResolver.resolve(componentName, componentVersion, teamcityBuildId)
        val buildMode = resolveBuildMode(resolvedVcs.commit.branch)
        val sastOverride = sonarExecutionResolver.getAppliedSastOverride(componentName)

        val projectContext = resolveProjectContext(resolvedVcs, sastOverride)
        val branchContext = resolveBranchContext(resolvedVcs, buildMode, sastOverride)

        val sonarServer = sonarServerResolver.resolveSonarServer(componentName)
        val skipMetarunnerExecution = sonarExecutionResolver.skipSonarMetarunnerExecution(componentName, componentVersion)
        val skipReportGeneration = sonarExecutionResolver.skipSonarReportGeneration(componentName)

        return SonarParametersDTO(
            sonarProjectKey = projectContext.projectKey,
            sonarProjectName = projectContext.projectName,
            sonarSourceBranch = branchContext.sourceBranch,
            sonarTargetBranch = branchContext.targetBranch,
            sonarExtraParameters = branchContext.sonarExtraParameters,
            sonarServerId = sonarServer.id,
            sonarServerUrl = sonarServer.url,
            skipSonarMetarunnerExecution = skipMetarunnerExecution,
            skipSonarReportGeneration = skipReportGeneration
        )
    }

    private fun resolveProjectContext(
        resolvedVcs: ResolvedVCSDTO,
        sastOverride: SonarProjectOverride?
    ): ProjectContext {
        if (sastOverride != null) {
            return ProjectContext(
                projectKey = sastOverride.sonarProjectKey,
                projectName = sastOverride.sonarProjectName
            )
        }
        return ProjectContext(
            projectKey = "${resolvedVcs.bbProjectKey}_${resolvedVcs.bbRepositoryKey}_$componentName",
            projectName = "${resolvedVcs.bbProjectKey}/${resolvedVcs.bbRepositoryKey}:$componentName"
        )
    }

    private fun resolveBranchContext(
        resolvedVcs: ResolvedVCSDTO,
        buildMode: BuildMode,
        sastOverride: SonarProjectOverride?
    ): BranchContext {
        if (sastOverride != null) {
            val candidates = resolvedVcs.defaultBranches.ifEmpty { DEFAULT_BRANCH_CANDIDATES }
            val targetBranch = targetBranchResolver.findTargetBranchBestEffort(resolvedVcs.commit, candidates)
            return BranchContext(
                sourceBranch = resolvedVcs.commit.branch,
                targetBranch = targetBranch,
                sonarExtraParameters = ""
            )
        }

        if (buildMode == BuildMode.PULL_REQUEST) {
            return BranchContext(
                sourceBranch = resolvedVcs.commit.branch,
                targetBranch = TC_PULL_REQUEST_TARGET_BRANCH_PARAM,
                sonarExtraParameters = SonarParameterBuilder.forPullRequest(
                    TC_PULL_REQUEST_NUMBER_PARAM,
                    TC_PULL_REQUEST_SOURCE_BRANCH_PARAM,
                    TC_PULL_REQUEST_TARGET_BRANCH_PARAM
                )
            )
        }

        val sourceBranch = resolvedVcs.commit.branch
        val candidates = resolvedVcs.defaultBranches.ifEmpty { DEFAULT_BRANCH_CANDIDATES }
        val targetBranch = targetBranchResolver.findTargetBranch(resolvedVcs.commit, candidates)

        return BranchContext(
            sourceBranch = sourceBranch,
            targetBranch = targetBranch,
            sonarExtraParameters = SonarParameterBuilder.forBranch(sourceBranch, targetBranch)
        )
    }

    private fun resolveBuildMode(sourceBranch: String): BuildMode {
        return if (sourceBranch.startsWith(PULL_REQUEST_BRANCH_MARKER)) {
            BuildMode.PULL_REQUEST
        } else {
            BuildMode.REGULAR_BRANCH
        }
    }

    private enum class BuildMode {
        PULL_REQUEST,
        REGULAR_BRANCH
    }

    private data class ProjectContext(
        val projectKey: String,
        val projectName: String
    )

    private data class BranchContext(
        val sourceBranch: String,
        val targetBranch: String,
        val sonarExtraParameters: String
    )

    companion object {
        private const val PULL_REQUEST_BRANCH_MARKER = "pull-requests/"
        private const val TC_PULL_REQUEST_NUMBER_PARAM = "%teamcity.pullRequest.number%"
        private const val TC_PULL_REQUEST_SOURCE_BRANCH_PARAM = "%teamcity.pullRequest.source.branch%"
        private const val TC_PULL_REQUEST_TARGET_BRANCH_PARAM = "%teamcity.pullRequest.target.branch%"
        private val DEFAULT_BRANCH_CANDIDATES = listOf("main", "master")
    }
}