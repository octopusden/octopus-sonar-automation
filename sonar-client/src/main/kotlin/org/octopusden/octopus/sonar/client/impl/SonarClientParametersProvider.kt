package org.octopusden.octopus.sonar.client.impl

interface SonarClientParametersProvider {
    fun getBaseUrl(): String
    fun getUsername(): String
    fun getPassword(): String
    fun getConnectTimeoutInMillis(): Long
    fun getReadTimeoutInMillis(): Long
}

