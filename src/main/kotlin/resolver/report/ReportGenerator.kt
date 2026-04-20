package org.octopusden.octopus.sonar.resolver.report

import org.octopusden.octopus.sonar.client.SonarClient
import java.io.File

/**
 * Orchestrates the SAST report generation pipeline:
 * 1. Fetches data from SonarQube (issues, hotspots, quality gate)
 * 2. Maps API DTOs to report data
 * 3. Renders HTML report
 * 4. Writes the file to disk
 */
class ReportGenerator(
    private val fetcher: ReportDataFetcher,
    private val mapper: ReportDataMapper,
    private val renderer: ReportHtmlRenderer,
) {
    constructor(sonarClient: SonarClient) : this(
        fetcher = ReportDataFetcher(sonarClient),
        mapper = ReportDataMapper(),
        renderer = ReportHtmlRenderer(),
    )

    /**
     * Generates the SAST report and writes it to the given [outputDir].
     *
     * @return the absolute path of the generated HTML file.
     */
    fun generate(
        projectKey: String,
        branch: String,
        componentName: String,
        componentVersion: String,
        sonarProjectName: String,
        sonarServerUrl: String,
        outputDir: File = File(DEFAULT_OUTPUT_DIR),
    ): File {
        val fetchedData = fetcher.fetch(projectKey, branch)

        val reportData = mapper.map(
            fetchedData = fetchedData,
            componentName = componentName,
            componentVersion = componentVersion,
            sonarProjectName = sonarProjectName,
            sonarServerUrl = sonarServerUrl,
            sonarProjectKey = projectKey,
            sourceBranch = branch
        )

        val html = renderer.render(reportData)

        val sanitizedName = componentName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val sanitizedVersion = componentVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "$sanitizedName-$sanitizedVersion-sast-report.html"

        if (outputDir.exists()) {
            require(outputDir.isDirectory) { "Output path exists but is not a directory: ${outputDir.absolutePath}" }
        } else if (!outputDir.mkdirs()) {
            throw IllegalStateException("Failed to create output directory: ${outputDir.absolutePath}")
        }
        
        val outputFile = File(outputDir, fileName)
        outputFile.writeText(html, Charsets.UTF_8)
        return outputFile
    }

    companion object {
        private const val DEFAULT_OUTPUT_DIR = "sonar-report"
    }
}