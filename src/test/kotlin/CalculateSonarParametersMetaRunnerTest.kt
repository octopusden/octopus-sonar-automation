package org.octopusden.octopus.sonar

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateSonarParametersMetaRunnerTest {
    private val metaRunner =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File("metarunners/CalculateSonarParameters.xml"))
            .documentElement

    private val fetchStep: Map<String, String> =
        metaRunner.getElementsByTagName("runner").let { nodes ->
            (0 until nodes.length)
                .map { nodes.item(it) as Element }
                .single { it.getAttribute("name") == "Fetch target branch" }
                .params()
        }

    private fun Element.params(): Map<String, String> =
        getElementsByTagName("param").let { nodes ->
            (0 until nodes.length)
                .map { nodes.item(it) as Element }
                .associate { it.getAttribute("name") to it.getAttribute("value") }
        }

    @Test
    fun `meta-runner declares WORK_DIR defaulting to the configuration's WORK_DIR`() {
        val parameters = metaRunner.getElementsByTagName("parameters").item(0) as Element
        assertEquals("%WORK_DIR%", parameters.params()["WORK_DIR"])
    }

    @Test
    fun `fetch step runs in WORK_DIR`() {
        assertEquals("%WORK_DIR%", fetchStep["teamcity.build.workingDir"])
    }

    @Test
    fun `fetch step runs only when the scan runs`() {
        assertEquals(
            """[["EQUALS","SKIP_SONAR_METARUNNER_EXECUTION","false"]]""",
            fetchStep["teamcity.step.conditions"],
        )
    }
}
