package com.example.gpsspeedometer.ui.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripsViewModelTest {

    @Test
    fun `test escapeCsv with normal text`() {
        val result = TripsViewModel.escapeCsv("Road Trip")
        assertEquals("Road Trip", result)
    }

    @Test
    fun `test escapeCsv with commas`() {
        val result = TripsViewModel.escapeCsv("Trip to Austin, TX")
        assertEquals("\"Trip to Austin, TX\"", result)
    }

    @Test
    fun `test escapeCsv with quotes`() {
        val result = TripsViewModel.escapeCsv("Trip \"Fast & Furious\"")
        assertEquals("\"Trip \"\"Fast & Furious\"\"\"", result)
    }

    @Test
    fun `test escapeCsv with newlines`() {
        val result = TripsViewModel.escapeCsv("Multi\nLine")
        assertEquals("\"Multi\nLine\"", result)
    }

    @Test
    fun `test escapeXml escapes special characters`() {
        val input = "Trip <1> & \"Fast\" 'Run'"
        val escaped = TripsViewModel.escapeXml(input)
        assertEquals("Trip &lt;1&gt; &amp; &quot;Fast&quot; &apos;Run&apos;", escaped)
    }

    @Test
    fun `test buildGpx generates valid XML structure`() {
        val trip = com.example.gpsspeedometer.data.TripEntity(
            id = 1L,
            name = "Mountain Highway",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            elapsedMs = 3600000L,
            distanceMeters = 50000f,
            maxSpeedMph = 65f,
            avgSpeedMph = 31f
        )
        val points = listOf(
            com.example.gpsspeedometer.data.TripPointEntity(
                id = 1L,
                tripId = 1L,
                timestamp = 1700000000000L,
                lat = 37.7749,
                lng = -122.4194,
                accuracyMeters = 5f,
                speedMps = 15f,
                rawMph = 33.5f,
                smoothedMph = 33.5f
            )
        )

        val gpx = TripsViewModel.buildGpx(trip, points)
        org.junit.Assert.assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        org.junit.Assert.assertTrue(gpx.contains("<gpx version=\"1.1\""))
        org.junit.Assert.assertTrue(gpx.contains("<name>Mountain Highway</name>"))
        org.junit.Assert.assertTrue(gpx.contains("<trkpt lat=\"37.7749\" lon=\"-122.4194\">"))
        org.junit.Assert.assertTrue(gpx.contains("<speed>15.0</speed>"))
        org.junit.Assert.assertTrue(gpx.endsWith("</gpx>\n"))
    }
}
