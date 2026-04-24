package org.octopusden.octopus.sonar.util

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class BitbucketSshUrlParserTest {

    // ── happy-path ────────────────────────────────────────────────────────────

    @Test
    fun `parses standard SSH URL and returns uppercase project and lowercase repo`() {
        val (project, repo) = BitbucketSshUrlParser.parseRepository(
            "ssh://git@bitbucket.example.com/MYPROJECT/my-repo.git"
        )
        assertEquals("MYPROJECT", project)
        assertEquals("my-repo", repo)
    }

    @Test
    fun `lowercases project key from input that is already lowercase`() {
        val (project, _) = BitbucketSshUrlParser.parseRepository(
            "ssh://git@bitbucket.example.com/myproject/repo.git"
        )
        assertEquals("MYPROJECT", project)
    }

    @Test
    fun `strips dot-git suffix from repository slug`() {
        val (_, repo) = BitbucketSshUrlParser.parseRepository(
            "ssh://git@bitbucket.example.com/PROJ/awesome-service.git"
        )
        assertEquals("awesome-service", repo)
    }

    @Test
    fun `handles repo slug that has no dot-git suffix`() {
        val (_, repo) = BitbucketSshUrlParser.parseRepository(
            "ssh://git@bitbucket.example.com/PROJ/my-repo"
        )
        assertEquals("my-repo", repo)
    }

    @Test
    fun `handles mixed-case repo slug and lowercases it`() {
        val (_, repo) = BitbucketSshUrlParser.parseRepository(
            "ssh://git@bitbucket.example.com/PROJ/MyRepo.git"
        )
        assertEquals("myrepo", repo)
    }

    @Test
    fun `handles different SSH user in URL`() {
        val (project, repo) = BitbucketSshUrlParser.parseRepository(
            "ssh://admin@bitbucket.internal/TOOLS/build-scripts.git"
        )
        assertEquals("TOOLS", project)
        assertEquals("build-scripts", repo)
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun `throws IllegalArgumentException for plain HTTPS URL`() {
        assertThrows<IllegalArgumentException> {
            BitbucketSshUrlParser.parseRepository("https://bitbucket.example.com/PROJ/repo.git")
        }
    }

    @Test
    fun `throws IllegalArgumentException for blank string`() {
        assertThrows<IllegalArgumentException> {
            BitbucketSshUrlParser.parseRepository("")
        }
    }

    @Test
    fun `throws IllegalArgumentException for git at-sign-only URL with no path`() {
        assertThrows<IllegalArgumentException> {
            BitbucketSshUrlParser.parseRepository("ssh://git@bitbucket.example.com")
        }
    }

    @Test
    fun `throws IllegalArgumentException for URL missing repository segment`() {
        assertThrows<IllegalArgumentException> {
            BitbucketSshUrlParser.parseRepository("ssh://git@bitbucket.example.com/PROJ")
        }
    }
}
