package org.octopusden.octopus.sonar.resolver

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.sonar.client.TeamcityRestClient
import org.octopusden.octopus.sonar.resolver.parameters.CommitStampResolver
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.REGISTERED
import org.octopusden.octopus.sonar.resolver.parameters.ComponentRegistration.UNREGISTERED
import org.octopusden.octopus.sonar.test.Fixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommitStampResolverTest {
    private lateinit var teamcityClient: TeamcityRestClient
    private lateinit var crsClient: ComponentsRegistryServiceClient
    private lateinit var resolver: CommitStampResolver

    @BeforeTest
    fun setUp() {
        teamcityClient = mockk()
        crsClient = mockk()
        resolver = CommitStampResolver(teamcityClient, crsClient)
    }

    // ── resolveWithoutVcsSettings ─────────────────────────────────────────────

    @Test
    fun `resolves single revision when VCS settings are NOT_AVAILABLE`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision()))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting("comp", "1.0") } returns Fixtures.noVcsSettings()

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals("MYPROJ", result.bbProjectKey)
        assertEquals("my-repo", result.bbRepositoryKey)
        assertEquals("main", result.commit.branch)
        assertEquals(listOf("main", "master"), result.defaultBranches)
    }

    @Test
    fun `normalises refs-heads prefix from branch name`() {
        every { teamcityClient.getBuildById(1) } returns
            Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision(branch = "refs/heads/feature/abc")))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.noVcsSettings()

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals("feature/abc", result.commit.branch)
    }

    @Test
    fun `uses first stamp when multiple stamps exist and VCS settings are NOT_AVAILABLE`() {
        val rev1 = Fixtures.tcRevision(version = "aaa", vcsRootInstanceId = 1)
        val rev2 = Fixtures.tcRevision(version = "bbb", vcsRootInstanceId = 2)

        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(rev1, rev2))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()
        every { teamcityClient.getVcsRootInstance(2) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.noVcsSettings()

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals("aaa", result.commit.cid)
    }

    // ── resolveWithVcsSettings ────────────────────────────────────────────────

    @Test
    fun `matches commit stamp to VCS root by SSH URL`() {
        val otherUrl = "ssh://git@bitbucket.example.com/OTHER/other-repo.git"
        val rev1 = Fixtures.tcRevision(version = "aaa", vcsRootInstanceId = 1)
        val rev2 = Fixtures.tcRevision(version = "bbb", vcsRootInstanceId = 2)

        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(rev1, rev2))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance(otherUrl)
        every { teamcityClient.getVcsRootInstance(2) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.vcsSettings()

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals("bbb", result.commit.cid)
        assertEquals("MYPROJ", result.bbProjectKey)
        assertEquals("my-repo", result.bbRepositoryKey)
    }

    @Test
    fun `uses pipe-separated branch list from VCS root as candidate branches`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision()))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.vcsSettings(branch = "main | release/1.0 | release/2.0")

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals(listOf("main", "release/1.0", "release/2.0"), result.defaultBranches)
    }

    @Test
    fun `falls back to default branches when VCS root branch field is blank`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision()))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.vcsSettings(branch = "")

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals(listOf("main", "master"), result.defaultBranches)
    }

    @Test
    fun `throws when no commit stamp URL matches any declared VCS root`() {
        val differentUrl = "ssh://git@bitbucket.example.com/OTHER/different.git"

        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision()))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance(differentUrl)
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.vcsSettings()

        assertFailsWith<IllegalStateException> {
            resolver.resolve("comp", "1.0", 1, REGISTERED)
        }
    }

    // ── CVS filtering ─────────────────────────────────────────────────────────

    @Test
    fun `skips CVS VCS roots and processes only Git roots`() {
        val cvsRevision = Fixtures.tcRevision(version = "cvs-rev", vcsRootInstanceId = 1)
        val gitRevision = Fixtures.tcRevision(version = "git-rev", vcsRootInstanceId = 2)

        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(cvsRevision, gitRevision))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcCvsRootInstance()
        every { teamcityClient.getVcsRootInstance(2) } returns Fixtures.tcVcsRootInstance()
        every { crsClient.getVCSSetting(any(), any()) } returns Fixtures.noVcsSettings()

        val result = resolver.resolve("comp", "1.0", 1, REGISTERED)

        assertEquals("git-rev", result.commit.cid)
    }

    // ── guard conditions ──────────────────────────────────────────────────────

    @Test
    fun `throws when build has no usable revisions after CVS filtering`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision(vcsRootInstanceId = 1)))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcCvsRootInstance()

        assertFailsWith<IllegalArgumentException> {
            resolver.resolve("comp", "1.0", 1, REGISTERED)
        }
    }

    // ── unregistered components ───────────────────────────────────────────────

    @Test
    fun `unregistered component resolves from the GitHub SCP-like remote`() {
        val githubUrl = "git@github.com:octopusden/octopus-external-systems-client.git"
        every { teamcityClient.getBuildById(1) } returns
            Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision(branch = "refs/heads/bitbucket-archived-flag")))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance(githubUrl)

        val result = resolver.resolve("octopus-external-systems-client", "2.0.105", 1, UNREGISTERED)

        assertEquals("OCTOPUSDEN", result.bbProjectKey)
        assertEquals("octopus-external-systems-client", result.bbRepositoryKey)
        assertEquals("bitbucket-archived-flag", result.commit.branch)
        assertEquals(listOf("main", "master"), result.defaultBranches)
    }

    @Test
    fun `unregistered component does not query VCS settings`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(listOf(Fixtures.tcRevision()))
        every { teamcityClient.getVcsRootInstance(1) } returns Fixtures.tcVcsRootInstance()

        resolver.resolve("octopus-comp", "1.0", 1, UNREGISTERED)

        verify(exactly = 0) { crsClient.getVCSSetting(any(), any()) }
    }

    @Test
    fun `unregistered component with no revisions still fails`() {
        every { teamcityClient.getBuildById(1) } returns Fixtures.tcBuildResponse(emptyList())

        assertFailsWith<IllegalArgumentException> {
            resolver.resolve("octopus-comp", "1.0", 1, UNREGISTERED)
        }
    }
}
