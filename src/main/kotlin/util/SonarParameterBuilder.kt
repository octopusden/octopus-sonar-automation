package org.octopusden.octopus.sonar.util

/**
 * Builds the `SONAR_EXTRA_PARAMETERS` string (the `-Dsonar.*` flags passed to the
 * Sonar scanner) based on whether the build is a pull-request or a regular branch build.
 */
object SonarParameterBuilder {

    fun forPullRequest(prKey: String, sourceBranch: String, targetBranch: String): String =
        listOf(
            "-Dsonar.pullrequest.key=$prKey",
            "-Dsonar.pullrequest.branch=$sourceBranch",
            "-Dsonar.pullrequest.base=$targetBranch"
        ).joinToString(" ")

    fun forBranch(sourceBranch: String, targetBranch: String): String {
        val params = mutableListOf("-Dsonar.branch.name=$sourceBranch")
        if (sourceBranch != targetBranch) {
            params += "-Dsonar.newCode.referenceBranch=$targetBranch"
        }
        return params.joinToString(" ")
    }

}
