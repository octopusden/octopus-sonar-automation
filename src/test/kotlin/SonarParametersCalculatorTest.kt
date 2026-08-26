package org.octopusden.octopus.sonar

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.sonar.dto.CommitStampDTO
import org.octopusden.octopus.sonar.dto.ResolvedVCSDTO
import org.octopusden.octopus.sonar.dto.SonarServerParametersDTO
import org.octopusden.octopus.sonar.resolver.parameters.CommitStampResolver
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.REGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.UNREGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistrationResolver
import org.octopusden.octopus.sonar.resolver.parameters.SonarExecutionResolver
import org.octopusden.octopus.sonar.resolver.parameters.SonarParametersCalculator
import org.octopusden.octopus.sonar.resolver.parameters.SonarProjectOverride
import org.octopusden.octopus.sonar.resolver.parameters.SonarServerResolver
import org.octopusden.octopus.sonar.resolver.parameters.TargetBranchResolver
import org.octopusden.octopus.sonar.util.SonarParameterBuilder
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
    private lateinit var componentRegistrationResolver: ComponentRegistrationResolver

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
        componentRegistrationResolver = mockk()
        every { componentRegistrationResolver.resolve("my-component") } returns REGISTERED

        calculator =
            SonarParametersCalculator(
                teamcityClient = teamcityClient,
                crsClient = crsClient,
                vcsFacadeClient = vcsFacadeClient,
                componentName = "my-component",
                componentVersion = "1.0.0",
                teamcityBuildId = 42,
                sonarConfigDir =
                    java.nio.file.Path
                        .of("/unused"),
                commitStampResolver = commitStampResolver,
                targetBranchResolver = targetBranchResolver,
                sonarServerResolver = sonarServerResolver,
                sonarExecutionResolver = sonarExecutionResolver,
                componentRegistrationResolver = componentRegistrationResolver,
            )
    }

    @Test
    fun `regular branch build computes project and branch parameters`() {
        val resolvedVcs = resolvedVcs(branch = "feature/abc")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns true
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("MYPROJ_my-repo_my-component", result.sonarProjectKey)
        assertEquals("MYPROJ/my-repo:my-component", result.sonarProjectName)
        assertEquals("feature/abc", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("feature/abc", "main"),
            result.sonarExtraParameters,
        )
        assertEquals(SonarServerParametersDTO.COMMUNITY.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.COMMUNITY.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.COMMUNITY.token, result.sonarServerToken)
        assertFalse(result.skipSonarMetarunnerExecution)
        assertTrue(result.skipSonarReportGeneration)
        assertEquals("%SONAR_GRADLE_TASK%", result.sonarPluginTask)

        verify(exactly = 1) { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) }
    }

    @Test
    fun `pull request build uses TeamCity pull request placeholders`() {
        val resolvedVcs = resolvedVcs(branch = "pull-requests/123")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.DEVELOPER
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("pull-requests/123", result.sonarSourceBranch)
        assertEquals("%teamcity.pullRequest.target.branch%", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forPullRequest(
                "%teamcity.pullRequest.number%",
                "%teamcity.pullRequest.source.branch%",
                "%teamcity.pullRequest.target.branch%",
            ),
            result.sonarExtraParameters,
        )

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override on PR branch uses PR parameters like regular PR`() {
        val resolvedVcs = resolvedVcs(branch = "pull-requests/456")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
            )
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns true
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("OVERRIDE_KEY", result.sonarProjectKey)
        assertEquals("OVERRIDE/NAME", result.sonarProjectName)
        assertEquals("pull-requests/456", result.sonarSourceBranch)
        assertEquals("%teamcity.pullRequest.target.branch%", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forPullRequest(
                "%teamcity.pullRequest.number%",
                "%teamcity.pullRequest.source.branch%",
                "%teamcity.pullRequest.target.branch%",
            ),
            result.sonarExtraParameters,
        )

        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override on feature branch produces branch parameters like regular build`() {
        val resolvedVcs = resolvedVcs(branch = "feature/sast-test")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
            )
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns true
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("OVERRIDE_KEY", result.sonarProjectKey)
        assertEquals("OVERRIDE/NAME", result.sonarProjectName)
        assertEquals("feature/sast-test", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("feature/sast-test", "main"),
            result.sonarExtraParameters,
        )

        verify(exactly = 1) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override on production branch produces branch parameters like regular build`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
            )
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("main", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("main", "main"),
            result.sonarExtraParameters,
        )

        verify(exactly = 1) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `applied sast override on feature branch resolves target and produces branch parameters`() {
        val resolvedVcs = resolvedVcs(branch = "feature/hotfix-1")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
            )
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("feature/hotfix-1", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("feature/hotfix-1", "main"),
            result.sonarExtraParameters,
        )

        verify(exactly = 1) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    private fun resolvedVcs(branch: String): ResolvedVCSDTO =
        ResolvedVCSDTO(
            commit =
                CommitStampDTO(
                    cid = "abc123",
                    branch = branch,
                    vcsUrl = "ssh://git@bitbucket.example.com/MYPROJ/my-repo.git",
                ),
            defaultBranches = listOf("main", "master"),
            bbProjectKey = "MYPROJ",
            bbRepositoryKey = "my-repo",
        )

    @Test
    fun `production branch build sets source and target to same branch`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("main", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forBranch("main", "main"),
            result.sonarExtraParameters,
        )
        assertEquals("%SONAR_GRADLE_TASK%", result.sonarPluginTask)
    }

    @Test
    fun `maven component produces sonar maven goal reference task`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns BuildSystem.MAVEN

        val result = calculator.calculate()

        assertEquals("%SONAR_MAVEN_GOAL%", result.sonarPluginTask)
    }

    @Test
    fun `skipped plugin produces empty plugin task`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("", result.sonarPluginTask)
    }

    @Test
    fun `developer edition server is propagated`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(any(), any()) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.DEVELOPER
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals(SonarServerParametersDTO.DEVELOPER.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.DEVELOPER.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.DEVELOPER.token, result.sonarServerToken)
    }

    @Test
    fun `sonarServer override in applied-sast forces Community and skips server resolver`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
                sonarServer = "community",
            )
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals(SonarServerParametersDTO.COMMUNITY.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.COMMUNITY.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.COMMUNITY.token, result.sonarServerToken)
        // sonarServerResolver must NOT be called when the override provides the server
        verify(exactly = 0) { sonarServerResolver.resolveSonarServer(any(), any()) }
    }

    @Test
    fun `sonarServer override in applied-sast forces Developer and skips server resolver`() {
        val resolvedVcs = resolvedVcs(branch = "main")
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
                sonarServer = "developer",
            )
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, resolvedVcs.defaultBranches) } returns "main"
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns true
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals(SonarServerParametersDTO.DEVELOPER.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.DEVELOPER.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.DEVELOPER.token, result.sonarServerToken)
        verify(exactly = 0) { sonarServerResolver.resolveSonarServer(any(), any()) }
    }

    @Test
    fun `empty defaultBranches falls back to default candidates`() {
        val resolvedVcs =
            ResolvedVCSDTO(
                commit = CommitStampDTO("abc123", "feature/xyz", "ssh://git@bitbucket.example.com/MYPROJ/my-repo.git"),
                defaultBranches = emptyList(),
                bbProjectKey = "MYPROJ",
                bbRepositoryKey = "my-repo",
            )
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, REGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { targetBranchResolver.findTargetBranch(resolvedVcs.commit, listOf("main", "master")) } returns "main"
        every { sonarServerResolver.resolveSonarServer("my-component", REGISTERED) } returns SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", REGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", REGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", REGISTERED) } returns BuildSystem.GRADLE

        val result = calculator.calculate()

        assertEquals("main", result.sonarTargetBranch)
        verify { targetBranchResolver.findTargetBranch(resolvedVcs.commit, listOf("main", "master")) }
    }

    // ── unregistered components ───────────────────────────────────────────────

    @Test
    fun `unregistered component gets main as target branch without touching the branch resolver`() {
        val resolvedVcs = unregisteredVcs(branch = "bitbucket-archived-flag")
        stubUnregistered(resolvedVcs)

        val result = calculator.calculate()

        assertEquals("bitbucket-archived-flag", result.sonarSourceBranch)
        assertEquals("main", result.sonarTargetBranch)
        verify(exactly = 0) { targetBranchResolver.findTargetBranch(any(), any()) }
    }

    @Test
    fun `unregistered component produces the full community metarunner parameter set`() {
        val resolvedVcs = unregisteredVcs(branch = "bitbucket-archived-flag")
        stubUnregistered(resolvedVcs)

        val result = calculator.calculate()

        assertEquals("OCTOPUSDEN_octopus-external-systems-client_my-component", result.sonarProjectKey)
        assertEquals("OCTOPUSDEN/octopus-external-systems-client:my-component", result.sonarProjectName)
        assertEquals(
            "-Dsonar.branch.name=bitbucket-archived-flag -Dsonar.newCode.referenceBranch=main",
            result.sonarExtraParameters,
        )
        assertEquals(
            "-Dsonar.branch.name=bitbucket-archived-flag\n-Dsonar.newCode.referenceBranch=main",
            result.sonarRunnerExtraParameters,
        )
        assertEquals(SonarServerParametersDTO.COMMUNITY.id, result.sonarServerId)
        assertEquals(SonarServerParametersDTO.COMMUNITY.url, result.sonarServerUrl)
        assertEquals(SonarServerParametersDTO.COMMUNITY.token, result.sonarServerToken)
        assertFalse(result.skipSonarMetarunnerExecution)
        assertFalse(result.skipSonarReportGeneration)
        assertEquals("", result.sonarPluginTask)
    }

    @Test
    fun `unregistered component on the default branch omits the reference branch flag`() {
        val resolvedVcs = unregisteredVcs(branch = "main")
        stubUnregistered(resolvedVcs)

        val result = calculator.calculate()

        assertEquals("main", result.sonarTargetBranch)
        assertEquals("-Dsonar.branch.name=main", result.sonarExtraParameters)
    }

    @Test
    fun `pull request build of an unregistered component still uses TeamCity placeholders`() {
        val resolvedVcs = unregisteredVcs(branch = "pull-requests/42")
        stubUnregistered(resolvedVcs)

        val result = calculator.calculate()

        assertEquals("%teamcity.pullRequest.target.branch%", result.sonarTargetBranch)
        assertEquals(
            SonarParameterBuilder.forPullRequest(
                "%teamcity.pullRequest.number%",
                "%teamcity.pullRequest.source.branch%",
                "%teamcity.pullRequest.target.branch%",
            ),
            result.sonarExtraParameters,
        )
    }

    @Test
    fun `unregistered component in applied-sast keeps the override identity and is still scanned`() {
        val resolvedVcs = unregisteredVcs(branch = "main")
        every { componentRegistrationResolver.resolve("my-component") } returns UNREGISTERED
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, UNREGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns
            SonarProjectOverride(
                sonarProjectKey = "OVERRIDE_KEY",
                sonarProjectName = "OVERRIDE/NAME",
                sonarServer = "developer",
            )
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", UNREGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", UNREGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", UNREGISTERED) } returns null

        val result = calculator.calculate()

        assertEquals("OVERRIDE_KEY", result.sonarProjectKey)
        assertEquals("OVERRIDE/NAME", result.sonarProjectName)
        assertEquals(SonarServerParametersDTO.DEVELOPER.id, result.sonarServerId)
        assertFalse(result.skipSonarMetarunnerExecution)
        verify(exactly = 0) { sonarServerResolver.resolveSonarServer(any(), any()) }
    }

    private fun stubUnregistered(resolvedVcs: ResolvedVCSDTO) {
        every { componentRegistrationResolver.resolve("my-component") } returns UNREGISTERED
        every { commitStampResolver.resolve("my-component", "1.0.0", 42, UNREGISTERED) } returns resolvedVcs
        every { sonarExecutionResolver.getAppliedSastOverride("my-component") } returns null
        every { sonarServerResolver.resolveSonarServer("my-component", UNREGISTERED) } returns
            SonarServerParametersDTO.COMMUNITY
        every { sonarExecutionResolver.skipSonarMetarunnerExecution("my-component", "1.0.0", UNREGISTERED) } returns false
        every { sonarExecutionResolver.skipSonarReportGeneration("my-component", UNREGISTERED) } returns false
        every { sonarExecutionResolver.resolveSonarPluginBuildSystem("my-component", "1.0.0", UNREGISTERED) } returns null
    }

    private fun unregisteredVcs(branch: String): ResolvedVCSDTO =
        ResolvedVCSDTO(
            commit =
                CommitStampDTO(
                    cid = "abc123",
                    branch = branch,
                    vcsUrl = "git@github.com:octopusden/octopus-external-systems-client.git",
                ),
            defaultBranches = listOf("main", "master"),
            bbProjectKey = "OCTOPUSDEN",
            bbRepositoryKey = "octopus-external-systems-client",
        )
}
