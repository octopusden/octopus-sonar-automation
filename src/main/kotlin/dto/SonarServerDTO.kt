package org.octopusden.octopus.sonar.dto

data class SonarServerParametersDTO(
    val id: String,
    val url: String
) {
    companion object {
        /** Language labels that require the Developer Edition SonarQube server. */
        val DEVELOPER_LABELS = setOf("c", "cpp", "objective_c", "swift")

        val DEVELOPER = SonarServerParametersDTO(
            id    = "%SONAR_DEVELOPER_ID%",
            url   = "%SONAR_DEVELOPER_URL%"
        )

        val COMMUNITY = SonarServerParametersDTO(
            id    = "%SONAR_COMMUNITY_ID%",
            url   = "%SONAR_COMMUNITY_URL%"
        )
    }
}
