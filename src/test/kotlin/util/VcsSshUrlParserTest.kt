package org.octopusden.octopus.sonar.util

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class VcsSshUrlParserTest {
    // ── ssh:// URLs ───────────────────────────────────────────────────────────

    @Test
    fun `parses standard SSH URL and returns uppercase project and lowercase repo`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/MYPROJECT/my-repo.git",
            )
        assertEquals("MYPROJECT", project)
        assertEquals("my-repo", repo)
    }

    @Test
    fun `lowercases project key from input that is already lowercase`() {
        val (project, _) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/myproject/repo.git",
            )
        assertEquals("MYPROJECT", project)
    }

    @Test
    fun `strips dot-git suffix from repository slug`() {
        val (_, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/PROJ/awesome-service.git",
            )
        assertEquals("awesome-service", repo)
    }

    @Test
    fun `handles repo slug that has no dot-git suffix`() {
        val (_, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/PROJ/my-repo",
            )
        assertEquals("my-repo", repo)
    }

    @Test
    fun `handles mixed-case repo slug and lowercases it`() {
        val (_, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/PROJ/MyRepo.git",
            )
        assertEquals("myrepo", repo)
    }

    @Test
    fun `handles different SSH user in URL`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://admin@bitbucket.internal/TOOLS/build-scripts.git",
            )
        assertEquals("TOOLS", project)
        assertEquals("build-scripts", repo)
    }

    @Test
    fun `handles SSH URL with explicit port`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com:7999/PROJ/My-Repo.git",
            )
        assertEquals("PROJ", project)
        assertEquals("my-repo", repo)
    }

    @Test
    fun `handles trailing slash on SSH URL`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "ssh://git@bitbucket.example.com/PROJ/my-repo/",
            )
        assertEquals("PROJ", project)
        assertEquals("my-repo", repo)
    }

    // ── SCP-like remotes ──────────────────────────────────────────────────────

    @Test
    fun `parses SCP-like GitHub remote`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "git@github.com:octopusden/octopus-external-systems-client.git",
            )
        assertEquals("OCTOPUSDEN", project)
        assertEquals("octopus-external-systems-client", repo)
    }

    @Test
    fun `uppercases project and lowercases repo of mixed-case SCP-like remote`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository(
                "git@github.com:octopusden/Octopus-Repo.git",
            )
        assertEquals("OCTOPUSDEN", project)
        assertEquals("octopus-repo", repo)
    }

    @Test
    fun `handles SCP-like remote without dot-git suffix`() {
        val (_, repo) =
            VcsSshUrlParser.parseRepository("git@github.com:octopusden/octopus-base")
        assertEquals("octopus-base", repo)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val (project, repo) =
            VcsSshUrlParser.parseRepository("  git@github.com:octopusden/repo.git  ")
        assertEquals("OCTOPUSDEN", project)
        assertEquals("repo", repo)
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun `throws IllegalArgumentException for plain HTTPS URL`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("https://bitbucket.example.com/PROJ/repo.git")
        }
    }

    @Test
    fun `throws IllegalArgumentException for HTTPS GitHub URL`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("https://github.com/octopusden/repo.git")
        }
    }

    @Test
    fun `throws IllegalArgumentException for blank string`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("")
        }
    }

    @Test
    fun `throws IllegalArgumentException for git at-sign-only URL with no path`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("ssh://git@bitbucket.example.com")
        }
    }

    @Test
    fun `throws IllegalArgumentException for URL missing repository segment`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("ssh://git@bitbucket.example.com/PROJ")
        }
    }

    @Test
    fun `throws IllegalArgumentException for SCP-like remote missing repository segment`() {
        assertThrows<IllegalArgumentException> {
            VcsSshUrlParser.parseRepository("git@github.com:octopusden")
        }
    }
}
