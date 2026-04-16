package org.octopusden.octopus.sonar.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SonarParameterBuilderTest {

    // ── forPullRequest ────────────────────────────────────────────────────────

    @Test
    fun `forPullRequest includes pull-request key flag`() {
        val result = SonarParameterBuilder.forPullRequest("42", "feature/abc", "main")
        assertTrue(result.contains("-Dsonar.pullrequest.key=42"))
    }

    @Test
    fun `forPullRequest includes source branch flag`() {
        val result = SonarParameterBuilder.forPullRequest("42", "feature/abc", "main")
        assertTrue(result.contains("-Dsonar.pullrequest.branch=feature/abc"))
    }

    @Test
    fun `forPullRequest includes base branch flag`() {
        val result = SonarParameterBuilder.forPullRequest("42", "feature/abc", "main")
        assertTrue(result.contains("-Dsonar.pullrequest.base=main"))
    }

    @Test
    fun `forPullRequest produces exactly three space-separated flags`() {
        val result = SonarParameterBuilder.forPullRequest("99", "src", "tgt")
        assertEquals(
            "-Dsonar.pullrequest.key=99 -Dsonar.pullrequest.branch=src -Dsonar.pullrequest.base=tgt",
            result
        )
    }

    @Test
    fun `forPullRequest works with TeamCity parameter references`() {
        val result = SonarParameterBuilder.forPullRequest(
            "%teamcity.pullRequest.number%",
            "%teamcity.pullRequest.source.branch%",
            "%teamcity.pullRequest.target.branch%"
        )
        assertTrue(result.contains("-Dsonar.pullrequest.key=%teamcity.pullRequest.number%"))
        assertTrue(result.contains("-Dsonar.pullrequest.branch=%teamcity.pullRequest.source.branch%"))
        assertTrue(result.contains("-Dsonar.pullrequest.base=%teamcity.pullRequest.target.branch%"))
    }

    // ── forBranch — same branch ───────────────────────────────────────────────

    @Test
    fun `forBranch on default branch emits only branch-name flag`() {
        val result = SonarParameterBuilder.forBranch("main", "main")
        assertEquals("-Dsonar.branch.name=main", result)
    }

    @Test
    fun `forBranch on default branch does not emit newCode reference flag`() {
        val result = SonarParameterBuilder.forBranch("main", "main")
        assertTrue(!result.contains("-Dsonar.newCode.referenceBranch"))
    }

    // ── forBranch — feature branch ────────────────────────────────────────────

    @Test
    fun `forBranch on feature branch emits branch-name flag`() {
        val result = SonarParameterBuilder.forBranch("feature/abc", "main")
        assertTrue(result.contains("-Dsonar.branch.name=feature/abc"))
    }

    @Test
    fun `forBranch on feature branch emits newCode reference branch flag`() {
        val result = SonarParameterBuilder.forBranch("feature/abc", "main")
        assertTrue(result.contains("-Dsonar.newCode.referenceBranch=main"))
    }

    @Test
    fun `forBranch feature branch produces correct full string`() {
        val result = SonarParameterBuilder.forBranch("feature/abc", "main")
        assertEquals("-Dsonar.branch.name=feature/abc -Dsonar.newCode.referenceBranch=main", result)
    }

    @Test
    fun `forBranch uses correct target when target is master`() {
        val result = SonarParameterBuilder.forBranch("hotfix/fix", "master")
        assertEquals("-Dsonar.branch.name=hotfix/fix -Dsonar.newCode.referenceBranch=master", result)
    }
}
