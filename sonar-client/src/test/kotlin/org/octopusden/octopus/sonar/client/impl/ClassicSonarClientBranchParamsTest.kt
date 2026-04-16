package org.octopusden.octopus.sonar.client.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassicSonarClientBranchParamsTest {

    // ── Regular branches ─────────────────────────────────────────────────────

    @Test
    fun `regular branch returns branch param`() {
        assertEquals(mapOf("branch" to "main"), ClassicSonarClient.branchParams("main"))
    }

    @Test
    fun `feature branch returns branch param`() {
        assertEquals(mapOf("branch" to "feature/ABC-123"), ClassicSonarClient.branchParams("feature/ABC-123"))
    }

    @Test
    fun `master branch returns branch param`() {
        assertEquals(mapOf("branch" to "master"), ClassicSonarClient.branchParams("master"))
    }

    @Test
    fun `release branch returns branch param`() {
        assertEquals(mapOf("branch" to "release/2.0"), ClassicSonarClient.branchParams("release/2.0"))
    }

    // ── Pull request branches ────────────────────────────────────────────────

    @Test
    fun `pull-requests branch extracts PR number`() {
        assertEquals(mapOf("pullRequest" to "42"), ClassicSonarClient.branchParams("pull-requests/42"))
    }

    @Test
    fun `pull-requests branch with large number`() {
        assertEquals(mapOf("pullRequest" to "99999"), ClassicSonarClient.branchParams("pull-requests/99999"))
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `branch containing pull-request without slash is treated as regular branch`() {
        assertEquals(mapOf("branch" to "pull-request-fix"), ClassicSonarClient.branchParams("pull-request-fix"))
    }

    @Test
    fun `branch named pull-request singular without trailing slash is regular branch`() {
        assertEquals(mapOf("branch" to "fix/pull-request"), ClassicSonarClient.branchParams("fix/pull-request"))
    }

    @Test
    fun `empty branch name returns branch param`() {
        assertEquals(mapOf("branch" to ""), ClassicSonarClient.branchParams(""))
    }
}
