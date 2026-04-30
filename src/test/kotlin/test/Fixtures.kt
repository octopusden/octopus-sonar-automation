package org.octopusden.octopus.sonar.test

import io.mockk.every
import io.mockk.mockk
import org.octopusden.octopus.components.registry.core.dto.BuildParametersDTO
import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.components.registry.core.dto.ComponentV1
import org.octopusden.octopus.components.registry.core.dto.DetailedComponent
import org.octopusden.octopus.components.registry.core.dto.RepositoryType
import org.octopusden.octopus.components.registry.core.dto.VCSSettingsDTO
import org.octopusden.octopus.components.registry.core.dto.VersionControlSystemRootDTO
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import java.util.Date

/**
 * Shared factory helpers used across resolver test classes.
 * Keeps test setup concise and consistent.
 */
object Fixtures {

    const val REPO_URL = "ssh://git@bitbucket.example.com/MYPROJ/my-repo.git"

    // ── CRS DTOs ──────────────────────────────────────────────────────────────

    fun componentV1(
        labels: List<String> = emptyList(),
        archived: Boolean = false
    ): ComponentV1 = mockk<ComponentV1>().also { cv ->
        every { cv.labels } returns labels.toSet()
        every { cv.archived } returns archived
    }

    fun detailedComponent(
        labels: Set<String> = emptySet(),
        archived: Boolean = false,
        javaVersion: String? = null,
        buildSystem: BuildSystem = BuildSystem.GRADLE
    ): DetailedComponent = mockk<DetailedComponent>().also { dc ->
        every { dc.labels } returns labels
        every { dc.archived } returns archived
        every { dc.buildSystem } returns buildSystem
        val bp = mockk<BuildParametersDTO>().also { every { it.javaVersion } returns javaVersion }
        every { dc.buildParameters } returns bp
    }

    fun vcsRoot(
        url: String = REPO_URL,
        branch: String = "main"
    ): VersionControlSystemRootDTO = VersionControlSystemRootDTO(
        name = "root",
        vcsPath = url,
        branch = branch,
        tag = null,
        type = RepositoryType.GIT,
        hotfixBranch = null
    )

    fun vcsSettings(
        url: String = REPO_URL,
        branch: String = "main"
    ): VCSSettingsDTO = VCSSettingsDTO(
        versionControlSystemRoots = listOf(vcsRoot(url, branch)),
        externalRegistry = null
    )

    fun noVcsSettings(): VCSSettingsDTO = VCSSettingsDTO(
        versionControlSystemRoots = emptyList(),
        externalRegistry = "NOT_AVAILABLE"
    )

    // ── VCS Facade DTOs ───────────────────────────────────────────────────────

    fun commit(hash: String, repoUrl: String = REPO_URL): Commit = Commit(
        hash = hash,
        message = "commit message",
        date = Date(),
        author = User("dev", "dev@example.com"),
        parents = emptyList(),
        link = "http://bitbucket.example.com/commits/$hash",
        repository = Repository(repoUrl, "http://bitbucket.example.com")
    )

    // ── TeamCity API maps ─────────────────────────────────────────────────────

    fun tcRevision(
        version: String = "abc123",
        branch: String = "refs/heads/main",
        vcsRootInstanceId: Int = 1
    ): Map<String, Any> = mapOf(
        "version" to version,
        "vcsBranchName" to branch,
        "vcs-root-instance" to mapOf("id" to vcsRootInstanceId.toString())
    )

    fun tcVcsRootInstance(url: String = REPO_URL): Map<String, Any> = mapOf(
        "vcsName" to "git",
        "properties" to mapOf(
            "property" to listOf(mapOf("name" to "url", "value" to url))
        )
    )

    fun tcCvsRootInstance(): Map<String, Any> = mapOf("vcsName" to "cvs")

    fun tcBuildResponse(revisions: List<Map<String, Any>>): Map<String, Any> = mapOf(
        "revisions" to mapOf("revision" to revisions)
    )
}
