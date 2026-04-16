package org.octopusden.octopus.sonar.client

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Thrown when a TeamCity REST API request fails.
 */
class TeamcityApiException(
    val path: String,
    val statusCode: Int,
    val responseBody: String,
) : RuntimeException("TeamCity REST request failed for '$path': HTTP $statusCode — $responseBody")

/**
 * Lightweight HTTP client for the TeamCity REST API.
 * TODO: To be replaced with octopus-external-system-clients
 */
class TeamcityRestClient(
    private val baseUrl: String,
    user: String,
    password: String
) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
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
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw TeamcityApiException(path, response.statusCode(), response.body())
        }

        return objectMapper.readValue(response.body())
    }
}