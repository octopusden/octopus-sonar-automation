package org.octopusden.octopus.sonar.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BranchUtilsTest {

    // ── prefix stripping ──────────────────────────────────────────────────────

    @Test
    fun `strips refs-heads prefix`() {
        assertEquals("main", "refs/heads/main".normalizedBranch())
    }

    @Test
    fun `strips refs prefix only when heads is absent`() {
        assertEquals("feature/my-feature", "refs/feature/my-feature".normalizedBranch())
    }

    @Test
    fun `strips heads prefix when refs is absent`() {
        assertEquals("release/1.0", "heads/release/1.0".normalizedBranch())
    }

    @Test
    fun `leaves plain branch name unchanged`() {
        assertEquals("main", "main".normalizedBranch())
    }

    @Test
    fun `leaves feature branch name unchanged`() {
        assertEquals("feature/JIRA-123-my-feature", "feature/JIRA-123-my-feature".normalizedBranch())
    }

    // ── suffix stripping ──────────────────────────────────────────────────────

    @Test
    fun `strips from suffix used in Bitbucket pull-request refs`() {
        assertEquals("pull-requests/42", "pull-requests/42/from".normalizedBranch())
    }

    @Test
    fun `strips to suffix used in Bitbucket pull-request refs`() {
        assertEquals("pull-requests/42", "pull-requests/42/to".normalizedBranch())
    }

    @Test
    fun `strips trailing slash`() {
        assertEquals("main", "main/".normalizedBranch())
    }

    // ── combined cases ────────────────────────────────────────────────────────

    @Test
    fun `keeps from suffix for non pull-request branches`() {
        assertEquals("feature/abc/from", "refs/heads/feature/abc/from".normalizedBranch())
    }

    @Test
    fun `strips refs-heads and trailing slash together`() {
        assertEquals("main", "refs/heads/main/".normalizedBranch())
    }

    @Test
    fun `handles pull-request branch with full refs-heads prefix`() {
        assertEquals("pull-requests/7", "refs/heads/pull-requests/7/from".normalizedBranch())
    }
}
