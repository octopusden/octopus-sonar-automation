package org.octopusden.octopus.sonar.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.octopusden.octopus.sonar.client.SonarClient
import org.octopusden.octopus.sonar.client.dto.HotspotsResponseDTO
import org.octopusden.octopus.sonar.client.dto.IssuesResponseDTO
import org.octopusden.octopus.sonar.client.dto.MeasuresResponseDTO
import org.octopusden.octopus.sonar.client.dto.QualityGateResponseDTO
import feign.Feign
import feign.Logger
import feign.QueryMap
import feign.Request
import feign.RequestLine
import feign.auth.BasicAuthRequestInterceptor
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.util.concurrent.TimeUnit

class ClassicSonarClient(
    private val parametersProvider: SonarClientParametersProvider,
    private val mapper: ObjectMapper = defaultMapper(),
) : SonarClient {

    /**
     * Internal Feign interface using [QueryMap] so that branch vs pull-request
     * routing and optional parameters are handled transparently.
     */
    private interface SonarFeignApi {
        @RequestLine("GET /api/measures/component")
        fun getMeasures(@QueryMap params: Map<String, Any>): MeasuresResponseDTO

        @RequestLine("GET /api/qualitygates/project_status")
        fun getQualityGateStatus(@QueryMap params: Map<String, Any>): QualityGateResponseDTO

        @RequestLine("GET /api/issues/search")
        fun searchIssues(@QueryMap params: Map<String, Any>): IssuesResponseDTO

        @RequestLine("GET /api/hotspots/search")
        fun searchHotspots(@QueryMap params: Map<String, Any>): HotspotsResponseDTO
    }

    private val api: SonarFeignApi = buildClient()

    override fun getMeasures(
        branch: String,
        component: String,
        metricKeys: String,
    ): MeasuresResponseDTO = api.getMeasures(
        branchParams(branch) + mapOf(
            "component" to component,
            "metricKeys" to metricKeys,
        )
    )

    override fun getQualityGateStatus(
        branch: String,
        projectKey: String,
    ): QualityGateResponseDTO = api.getQualityGateStatus(
        branchParams(branch) + mapOf("projectKey" to projectKey)
    )

    override fun searchIssues(
        componentKeys: String,
        branch: String,
        resolved: Boolean,
        ps: Int,
        p: Int,
        inNewCodePeriod: Boolean?,
    ): IssuesResponseDTO {
        val params = branchParams(branch) + mutableMapOf<String, Any>(
            "componentKeys" to componentKeys,
            "resolved" to resolved,
            "ps" to ps,
            "p" to p,
        ).also {
            if (inNewCodePeriod != null) it["inNewCodePeriod"] = inNewCodePeriod
        }
        return api.searchIssues(params)
    }

    override fun searchHotspots(
        project: String,
        branch: String,
        status: String,
        ps: Int,
        p: Int,
    ): HotspotsResponseDTO = api.searchHotspots(
        branchParams(branch) + mapOf(
            "project" to project,
            "status" to status,
            "ps" to ps,
            "p" to p,
        )
    )

    private fun branchParams(branch: String): Map<String, Any> = ClassicSonarClient.branchParams(branch)

    private fun buildClient(): SonarFeignApi =
        Feign.builder()
            .requestInterceptor(
                BasicAuthRequestInterceptor(
                    parametersProvider.getUsername(),
                    parametersProvider.getPassword(),
                )
            )
            .options(
                Request.Options(
                    parametersProvider.getConnectTimeoutInMillis(),
                    TimeUnit.MILLISECONDS,
                    parametersProvider.getReadTimeoutInMillis(),
                    TimeUnit.MILLISECONDS,
                    true,
                )
            )
            .encoder(JacksonEncoder(mapper))
            .decoder(JacksonDecoder(mapper))
            .logger(Slf4jLogger(SonarClient::class.java))
            .logLevel(Logger.Level.BASIC)
            .target(SonarFeignApi::class.java, parametersProvider.getBaseUrl())

    companion object {
        /**
         * Translates a branch identifier into the correct SonarQube query parameter map.
         */
        internal fun branchParams(branch: String): Map<String, Any> {
            return if (branch.startsWith("pull-requests/")) {
                mapOf("pullRequest" to branch.substringAfter("pull-requests/"))
            } else {
                mapOf("branch" to branch)
            }
        }

        private fun defaultMapper(): ObjectMapper =
            jacksonObjectMapper().apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
    }
}
