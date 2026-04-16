package org.octopusden.octopus.sonar.client

import org.octopusden.octopus.sonar.client.dto.HotspotsResponseDTO
import org.octopusden.octopus.sonar.client.dto.IssuesResponseDTO
import org.octopusden.octopus.sonar.client.dto.MeasuresResponseDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateResponseDTO

/**
 * Clean interface for interacting with the SonarQube API.
 *
 * All methods accept a [branch] parameter which can be either a branch name
 * (e.g. `"master"`) or a pull-request reference (e.g. `"pull-requests/123"`).
 * The implementation transparently translates this into the correct
 * `&branch=` or `&pullRequest=` query parameter.
 */
interface SonarClient {

    fun getMeasures(
        branch: String,
        component: String,
        metricKeys: String,
    ): MeasuresResponseDTO

    fun getQualityGateStatus(
        branch: String,
        projectKey: String,
    ): QualityGateResponseDTO

    /**
     * Searches for issues in SonarQube.
     *
     * @param inNewCodePeriod when `true`, restricts results to the new code period.
     *                        When `null`, the parameter is omitted from the request.
     */
    fun searchIssues(
        componentKeys: String,
        branch: String,
        resolved: Boolean,
        ps: Int,
        p: Int,
        inNewCodePeriod: Boolean? = null,
    ): IssuesResponseDTO

    fun searchHotspots(
        project: String,
        branch: String,
        status: String,
        ps: Int,
        p: Int,
    ): HotspotsResponseDTO
}

