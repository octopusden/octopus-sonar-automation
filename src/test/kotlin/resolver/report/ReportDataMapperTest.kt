package org.octopusden.octopus.sonar.resolver.report

import kotlin.test.Test
import kotlin.test.assertEquals

class ReportDataMapperTest {

    @Test
    fun `extractRepository from PROJECT_repo_component format`() {
        assertEquals("PS/payment-service", ReportDataMapper.extractRepository("PS/payment-service:module"))
    }

    @Test
    fun `extractRepository with no colon returns full name`() {
        assertEquals("PS/payment-service", ReportDataMapper.extractRepository("PS/payment-service"))
    }

    @Test
    fun `extractFileName from component path`() {
        assertEquals("Foo.java", ReportDataMapper.extractFileName("proj:src/main/java/com/example/Foo.java"))
    }

    @Test
    fun `extractFileName with no colon`() {
        assertEquals("Foo.java", ReportDataMapper.extractFileName("src/main/java/com/example/Foo.java"))
    }

    @Test
    fun `extractFileName simple name`() {
        assertEquals("Foo.java", ReportDataMapper.extractFileName("Foo.java"))
    }

    @Test
    fun `formatEffort adds space between number and unit`() {
        assertEquals("15 min", ReportDataMapper.formatEffort("15min"))
        assertEquals("2 h", ReportDataMapper.formatEffort("2h"))
        assertEquals("1 h 30 min", ReportDataMapper.formatEffort("1h 30min"))
    }

    @Test
    fun `formatEffortTotal converts minutes to human readable`() {
        assertEquals("0 min", ReportDataMapper.formatEffortTotal(0))
        assertEquals("10 min", ReportDataMapper.formatEffortTotal(10))
        assertEquals("1 h", ReportDataMapper.formatEffortTotal(60))
        assertEquals("1 h 30 min", ReportDataMapper.formatEffortTotal(90))
        assertEquals("2 h 15 min", ReportDataMapper.formatEffortTotal(135))
    }

    @Test
    fun `mapLegacySeverity maps legacy severity to impact severity`() {
        assertEquals("BLOCKER", ReportDataMapper.mapLegacySeverity("BLOCKER"))
        assertEquals("HIGH", ReportDataMapper.mapLegacySeverity("CRITICAL"))
        assertEquals("MEDIUM", ReportDataMapper.mapLegacySeverity("MAJOR"))
        assertEquals("LOW", ReportDataMapper.mapLegacySeverity("MINOR"))
        assertEquals("INFO", ReportDataMapper.mapLegacySeverity("INFO"))
    }

    @Test
    fun `mapLegacySeverity is case insensitive`() {
        assertEquals("HIGH", ReportDataMapper.mapLegacySeverity("critical"))
        assertEquals("MEDIUM", ReportDataMapper.mapLegacySeverity("Major"))
    }

    @Test
    fun `mapLegacySeverity returns original value for unknown severity`() {
        assertEquals("UNKNOWN", ReportDataMapper.mapLegacySeverity("UNKNOWN"))
    }
}
