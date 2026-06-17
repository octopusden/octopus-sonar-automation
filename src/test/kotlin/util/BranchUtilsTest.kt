package org.octopusden.octopus.sonar.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchUtilsTest {

    // ── normalizedBranch: prefix stripping ────────────────────────────────────

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

    // ── normalizedBranch: trailing slash ──────────────────────────────────────

    @Test
    fun `strips trailing slash`() {
        assertEquals("main", "main/".normalizedBranch())
    }

    @Test
    fun `strips refs-heads and trailing slash together`() {
        assertEquals("main", "refs/heads/main/".normalizedBranch())
    }

    // ── normalizedBranch: pull-request branches keep their suffix ─────────────

    @Test
    fun `pull-request branch with from suffix is kept intact`() {
        // /from suffix is preserved — callers use isPullRequestBranch() to distinguish
        // native TC PR builds (pull-requests/<id>) from branch-filter builds (pull-requests/<id>/from)
        assertEquals("pull-requests/42/from", "pull-requests/42/from".normalizedBranch())
    }

    @Test
    fun `pull-request branch with to suffix is kept intact`() {
        assertEquals("pull-requests/42/to", "pull-requests/42/to".normalizedBranch())
    }

    @Test
    fun `native pull-request branch without suffix is unchanged`() {
        assertEquals("pull-requests/42", "pull-requests/42".normalizedBranch())
    }

    @Test
    fun `pull-request branch with refs-heads prefix and from suffix`() {
        assertEquals("pull-requests/7/from", "refs/heads/pull-requests/7/from".normalizedBranch())
    }

    // ── normalizedBranch: non-PR branches with from-like segments ────────────

    @Test
    fun `from segment in non-PR branch is kept`() {
        assertEquals("feature/abc/from", "refs/heads/feature/abc/from".normalizedBranch())
    }

    // ── isPullRequestBranch ───────────────────────────────────────────────────

    @Test
    fun `isPullRequestBranch returns true for native TC PR branch`() {
        assertTrue(BranchConstants.isPullRequestBranch("pull-requests/42"))
    }

    @Test
    fun `isPullRequestBranch returns false for pull-request branch with from suffix`() {
        assertFalse(BranchConstants.isPullRequestBranch("pull-requests/42/from"))
    }

    @Test
    fun `isPullRequestBranch returns false for pull-request branch with to suffix`() {
        assertFalse(BranchConstants.isPullRequestBranch("pull-requests/42/to"))
    }

    @Test
    fun `isPullRequestBranch returns false for regular branch`() {
        assertFalse(BranchConstants.isPullRequestBranch("main"))
    }

    @Test
    fun `isPullRequestBranch returns false for feature branch`() {
        assertFalse(BranchConstants.isPullRequestBranch("feature/my-feature"))
    }
}
