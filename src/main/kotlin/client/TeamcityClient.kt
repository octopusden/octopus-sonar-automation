package org.octopusden.octopus.sonar.client

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Lightweight HTTP client for the TeamCity REST API.
 * TODO: To be replaced with octopus-external-system-clients
 */
class TeamcityRestClient(
    private val baseUrl: String,
    user: String,
    password: String
) {

    private val client: HttpClient = HttpClient.newHttpClient()
    private val objectMapper = jacksonObjectMapper()

    private val authHeader: String = "Basic " + Base64.getEncoder()
        .encodeToString("$user:$password".toByteArray())

    fun getBuildById(buildId: Int): Map<String, Any> = getRequest("builds/id:$buildId")

    fun getVcsRootInstance(id: Int): Map<String, Any> = getRequest("vcs-root-instances/id:$id")

    private fun getRequest(path: String): Map<String, Any> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/app/rest/$path"))
            .header("Accept", "application/json")
            .header("Authorization", authHeader)
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw RuntimeException(
                "TeamCity REST request failed for '$path': HTTP ${response.statusCode()} — ${response.body()}"
            )
        }

        return objectMapper.readValue(response.body())
    }
}