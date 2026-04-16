package org.octopusden.octopus.sonar.resolver

import org.octopusden.octopus.sonar.dto.CommitStampDTO
import org.octopusden.octopus.sonar.resolver.parameters.TargetBranchResolver
import org.octopusden.octopus.sonar.test.Fixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TargetBranchResolverTest {

    private lateinit var vcsFacadeClient: ClassicVcsFacadeClient
    private lateinit var resolver: TargetBranchResolver
    private val fixedNowMillis = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        vcsFacadeClient = mockk()
        resolver = TargetBranchResolver(
            vcsFacadeClient = vcsFacadeClient,
            initialWindowDays = 10,
            maxWindowDays = 40,
            windowGrowthFactor = 2,
            nowProviderMillis = { fixedNowMillis },
        )
    }

    private fun stamp(branch: String) =
        CommitStampDTO(cid = "abc", branch = branch, vcsUrl = Fixtures.REPO_URL)

    private fun fromDate(days: Int): Date = Date(fixedNowMillis - days * DAY_IN_MILLIS)

    /** Stubs [vcsFacadeClient.getCommits] for the given [branchOrRef] to return [commits]. */
    private fun stubCommits(branchOrRef: String, commits: List<Commit>) {
        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = branchOrRef,
                fromDate = any(),
                fromHashOrRef = null
            )
        } returns commits
    }

    /** Stubs [vcsFacadeClient.getCommits] for the given [branchOrRef] to throw [exception]. */
    private fun stubCommitsThrows(branchOrRef: String, exception: Exception) {
        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = branchOrRef,
                fromDate = any(),
                fromHashOrRef = null
            )
        } throws exception
    }

    // ── source branch is itself a candidate ────────────────────────────────────

    @Test
    fun `returns single candidate immediately without VCS calls`() {
        val result = resolver.findTargetBranch(stamp("feature/abc"), listOf("main"))
        assertEquals("main", result)
        verify(exactly = 0) { vcsFacadeClient.getCommits(any(), any(), any(), any()) }
    }

    @Test
    fun `returns source branch immediately when it is in the candidates list`() {
        val result = resolver.findTargetBranch(stamp("main"), listOf("main", "master"))
        assertEquals("main", result)
    }

    @Test
    fun `does not call VCS Facade when source branch is a candidate`() {
        resolver.findTargetBranch(stamp("main"), listOf("main", "master"))
        verify(exactly = 0) { vcsFacadeClient.getCommits(any(), any(), any(), any()) }
    }

    @Test
    fun `returns master when source branch is master and candidates include both`() {
        val result = resolver.findTargetBranch(stamp("master"), listOf("main", "master"))
        assertEquals("master", result)
    }

    // ── diverge-point detection ───────────────────────────────────────────────

    @Test
    fun `returns main when source branch shares common commit with main`() {
        // source: f2 -> f1 -> shared (branched from main at 'shared')
        // main:   m1 -> m2 -> shared -> m3
        stubCommits("feature/abc", listOf(Fixtures.commit("f2"), Fixtures.commit("f1"), Fixtures.commit("shared")))
        stubCommits("main",        listOf(Fixtures.commit("m3"), Fixtures.commit("shared"), Fixtures.commit("m1")))

        val result = resolver.findTargetBranch(stamp("feature/abc"), listOf("main", "master"))
        assertEquals("main", result)

        verify(exactly = 0) {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "master",
                fromDate = any(),
                fromHashOrRef = null,
            )
        }
    }

    @Test
    fun `returns master when source branch diverged from master not main`() {
        stubCommits("feature/from-master", listOf(Fixtures.commit("fm2"), Fixtures.commit("fm1"), Fixtures.commit("shared-m")))
        stubCommits("main",                listOf(Fixtures.commit("mn1"), Fixtures.commit("mn2")))
        stubCommits("master",              listOf(Fixtures.commit("shared-m"), Fixtures.commit("m1")))

        val result = resolver.findTargetBranch(stamp("feature/from-master"), listOf("main", "master"))
        assertEquals("master", result)
    }

    // ── candidate branch not found ────────────────────────────────────────────

    @Test
    fun `skips candidate that throws NotFoundException and matches the other`() {
        stubCommits("feature/abc", listOf(Fixtures.commit("f1"), Fixtures.commit("shared")))
        stubCommitsThrows("main", NotFoundException("main not found"))
        stubCommits("master", listOf(Fixtures.commit("shared")))

        val result = resolver.findTargetBranch(stamp("feature/abc"), listOf("main", "master"))
        assertEquals("master", result)
    }

    // ── fallback behaviour ────────────────────────────────────────────────────

    @Test
    fun `falls back to first candidate when VCS Facade throws for source branch`() {
        stubCommitsThrows("feature/abc", RuntimeException("network error"))

        val result = resolver.findTargetBranch(stamp("feature/abc"), listOf("main", "master"))
        assertEquals("main", result)
    }

    @Test
    fun `falls back to first candidate when source branch has no commits`() {
        stubCommits("feature/empty", emptyList())

        val result = resolver.findTargetBranch(stamp("feature/empty"), listOf("main", "master"))
        assertEquals("main", result)
    }

    @Test
    fun `falls back to first candidate when no common commit is found`() {
        stubCommits("orphan", listOf(Fixtures.commit("x1"), Fixtures.commit("x2")))
        stubCommits("main",   listOf(Fixtures.commit("y1"), Fixtures.commit("y2")))
        stubCommits("master", listOf(Fixtures.commit("z1"), Fixtures.commit("z2")))

        val result = resolver.findTargetBranch(stamp("orphan"), listOf("main", "master"))
        assertEquals("main", result)
    }

    // ── guard conditions ──────────────────────────────────────────────────────

    @Test
    fun `throws IllegalArgumentException when candidates list is empty`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.findTargetBranch(stamp("feature/abc"), emptyList())
        }
    }

    @Test
    fun `widens search window when initial window has no match`() {
        val tenDays = fromDate(10)
        val twentyDays = fromDate(20)

        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "feature/slow-diverge",
                fromDate = tenDays,
                fromHashOrRef = null,
            )
        } returns listOf(Fixtures.commit("f-new"))

        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "feature/slow-diverge",
                fromDate = twentyDays,
                fromHashOrRef = null,
            )
        } returns listOf(Fixtures.commit("f-new"), Fixtures.commit("shared"))

        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "main",
                fromDate = tenDays,
                fromHashOrRef = null,
            )
        } returns listOf(Fixtures.commit("m-new"))

        every {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "main",
                fromDate = twentyDays,
                fromHashOrRef = null,
            )
        } returns listOf(Fixtures.commit("m-new"), Fixtures.commit("shared"))

        val result = resolver.findTargetBranch(stamp("feature/slow-diverge"), listOf("main", "master"))
        assertEquals("main", result)

        verify(exactly = 0) {
            vcsFacadeClient.getCommits(
                sshUrl = Fixtures.REPO_URL,
                toHashOrRef = "master",
                fromDate = any(),
                fromHashOrRef = null,
            )
        }
    }

    // ── findTargetBranchBestEffort ──────────────────────────────────────────

    @Test
    fun `best-effort returns source branch when it matches a candidate`() {
        val result = resolver.findTargetBranchBestEffort(stamp("main"), listOf("main", "master"))
        assertEquals("main", result)
        verify(exactly = 0) { vcsFacadeClient.getCommits(any(), any(), any(), any()) }
    }

    @Test
    fun `best-effort returns first candidate when source branch does not match`() {
        val result = resolver.findTargetBranchBestEffort(stamp("feature/abc"), listOf("main", "master"))
        assertEquals("main", result)
        verify(exactly = 0) { vcsFacadeClient.getCommits(any(), any(), any(), any()) }
    }

    @Test
    fun `best-effort returns single candidate regardless of source branch`() {
        val result = resolver.findTargetBranchBestEffort(stamp("feature/xyz"), listOf("main"))
        assertEquals("main", result)
    }

    @Test
    fun `best-effort throws when candidates list is empty`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.findTargetBranchBestEffort(stamp("feature/abc"), emptyList())
        }
    }

    companion object {
        private const val DAY_IN_MILLIS = 24L * 60 * 60 * 1000
    }
}
