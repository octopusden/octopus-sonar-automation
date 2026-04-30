package org.octopusden.octopus.sonar

import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.sonar.dto.CommitStampDTO
import org.octopusden.octopus.sonar.dto.ResolvedVCSDTO
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.resolver.parameters.CommitStampResolver
import org.octopusden.octopus.sonar.resolver.parameters.SonarExecutionResolver
import org.octopusden.octopus.sonar.resolver.parameters.SonarParametersCalculator
import org.octopusden.octopus.sonar.resolver.parameters.SonarProjectOverride
import org.octopusden.octopus.sonar.resolver.parameters.SonarServerResolver
import org.octopusden.octopus.sonar.resolver.parameters.TargetBranchResolver
import org.octopusden.octopus.sonar.util.SonarParameterBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SonarParametersCalculatorTest {

    private lateinit var teamcityClient: TeamcityRestClient
    private lateinit var crsClient: ClassicComponentsRegistryServiceClient
    private lateinit var vcsFacadeClient: ClassicVcsFacadeClient

    private lateinit var commitStampResolver: CommitStampResolver
    private lateinit var targetBranchResolver: TargetBranchResolver
    private lateinit var sonarServerResolver: SonarServerResolver
    private lateinit var sonarExecutionResolver: SonarExecutionResolver

    private lateinit var calculator: SonarParametersCalculator

    @BeforeTest
    fun setUp() {
        teamcityClient = mockk(relaxed = true)
        crsClient = mockk(relaxed = true)
        vcsFacadeClient = mockk(relaxed = true)

        commitStampResolver = mockk()
        targetBranchResolver = mockk()
        sonarServerResolver = mockk()
        sonarExecutionResolver = mockk()

        calculator = SonarParametersCalculator(
            teamcityClient = teamcityClient,
            crsClient = crsClient,
            vcsFacadeClient = vcsFacadeClient,
            componentName = "my-component",
            componentVersion = "1.0.0",
            teamcityBuildId = 42,
            sonarConfigDir = java.nio.file.Path.of("/unused"),
            commitStampResolver = commitStampResolver,
            targetBranchResolver = targetBranchResolver,
            sonarServerResolver = sonarServerResolver,
            sonarExecutionResolver = sonarExecutionResolver
        )
    }

    @Test
    fun `regular branch build computes project and branch parameters`() {
        val resolvedVcs = resolvedVcs(branch = "feature/abc")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns true
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("MYPROJ_my-repo_my-component", result.sonarProjectKey)
        assertEquals("MYPROJ/my-repo:my-component", result.sonarProjectName)
        assertEquals("feature/abc", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("feature/abc", "main"),
            result.sonarExtraParameters
        )
        assertEquals(SonarServerParametersDTO.COMMUNITY.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.COMMUNITY.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.COMMUNITY.token, result.sonarServerToken)
        assertFalse(result.skipSonarMetarunnerExecution)
        assertTrue(result.skipSonarReportGeneration)
        assertEquals("sonar", result.sonarPluginTask)

        verify(exactly = 1) { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) }
    }

    @Test
    fun `pull request build uses TeamCity pull request placeholders`() {
        val resolvedVcs = resolvedVcs(branch = "pull-requests/123")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.DEVELOPER
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns null

        val result = calculator.calculate()

        assertEquals("pull-requests/123", result.sonarSourceBranch)
        assertEquals("%teamcity.pullRequest.target.branch%", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forPullRequest(
                "%teamcity.pullRequest.number%",
                "%teamcity.pullRequest.source.branch%",
                "%teamcity.pullRequest.target.branch%"
            ),
            result.sonarExtraParameters
        )

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override keeps project override and resolves target branch best-effort`() {
        val resolvedVcs = resolvedVcs(branch = "pull-requests/456")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns SonarProjectOverride(
            sonarProjectKey = "OVERRIDE_KEY",
            sonarProjectName = "OVERRIDE/NAME"
        )
        every { targetBranchResolver.findTargetBranchBestEffort(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns true
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns null

        val result = calculator.calculate()

        assertEquals("OVERRIDE_KEY", result.sonarProjectKey)
        assertEquals("OVERRIDE/NAME", result.sonarProjectName)
        assertEquals("pull-requests/456", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals("", result.sonarExtraParameters)

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
        verify(exactly = 1) { targetBranchResolver.findTargetBranchBestEffort(any(), any()) }
    }

    @Test
    fun `applied sast override on production branch returns matching target branch`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns SonarProjectOverride(
            sonarProjectKey = "OVERRIDE_KEY",
            sonarProjectName = "OVERRIDE/NAME"
        )
        every { targetBranchResolver.findTargetBranchBestEffort(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns null

        val result = calculator.calculate()

        assertEquals("main", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals("", result.sonarExtraParameters)

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override on feature branch returns first candidate as target`() {
        val resolvedVcs = resolvedVcs(branch = "feature/hotfix-1")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns SonarProjectOverride(
            sonarProjectKey = "OVERRIDE_KEY",
            sonarProjectName = "OVERRIDE/NAME"
        )
        every { targetBranchResolver.findTargetBranchBestEffort(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns null

        val result = calculator.calculate()

        assertEquals("feature/hotfix-1", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals("", result.sonarExtraParameters)

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    private fun resolvedVcs(branch: String): ResolvedVCSDTO = ResolvedVCSDTO(
        commit = CommitStampDTO(
            cid = "abc123",
            branch = branch,
            vcsUrl = "ssh://git@bitbucket.example.com/MYPROJ/my-repo.git"
        ),
        defaultBranches = listOf("main", "master"),
        bbProjectKey = "MYPROJ",
        bbRepositoryKey = "my-repo"
    )

    @Test
    fun `production branch build sets source and target to same branch`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("main", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("main", "main"),
            result.sonarExtraParameters
        )
        assertEquals("sonar", result.sonarPluginTask)
    }

    @Test
    fun `maven component produces sonar colon sonar plugin task`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns BuildSystem.MAVEN

        val result = calculator.calculate()

        assertEquals("sonar:sonar", result.sonarPluginTask)
    }

    @Test
    fun `skipped plugin produces empty plugin task`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns null

        val result = calculator.calculate()

        assertEquals("", result.sonarPluginTask)
    }

    @Test
    fun `developer edition server is propagated`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(any(), any()) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.DEVELOPER
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals(SonarServerParametersDTO.DEVELOPER.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.DEVELOPER.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.DEVELOPER.token, result.sonarServerToken)
    }

    @Test
    fun `empty defaultBranches falls back to default candidates`() {
        val resolvedVcs = ResolvedVCSDTO(
            commit = CommitStampDTO("abc123", "feature/xyz", "ssh://git@bitbucket.example.com/MYPROJ/my-repo.git"),
            defaultBranches = emptyList(),
            bbProjectKey = "MYPROJ",
            bbRepositoryKey = "my-repo"
        )
        every { commitStampResolver.resolve("my-component", "1.0.0", 42) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, listOf("main", "master")) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component") } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0") } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component") } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0") } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("main", result.sonarTargetBranch)
        verify { targetBranchResolver.findTargetBranch(resolvedVcs.commit, listOf("main", "master")) }
    }
}
