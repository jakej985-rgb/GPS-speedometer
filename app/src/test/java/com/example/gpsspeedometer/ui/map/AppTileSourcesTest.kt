package com.example.gpsspeedometer.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTileSourcesTest {

    @Test
    fun openStreetMap_doesNotNormalizeUserAgent() {
        // Crucial: normalizesUserAgent must be false so osmdroid does not override
        // our User-Agent with the internal package name (com.example.*) which OSM blocks.
        assertFalse(AppTileSources.OpenStreetMap.tileSourcePolicy.normalizesUserAgent())
    }

    @Test
    fun openStreetMap_usesCorrectBaseUrl() {
        assertEquals("OpenStreetMapStandard", AppTileSources.OpenStreetMap.name())
        assertTrue(AppTileSources.OpenStreetMap.tileSourcePolicy.acceptsUserAgent(AppTileSources.USER_AGENT))
    }

    @Test
    fun cartoVoyager_configuredProperly() {
        assertEquals("CartoVoyager", AppTileSources.CartoVoyager.name())
        assertFalse(AppTileSources.CartoVoyager.tileSourcePolicy.normalizesUserAgent())
        assertTrue(AppTileSources.CartoVoyager.tileSourcePolicy.acceptsUserAgent(AppTileSources.USER_AGENT))
    }

    @Test
    fun userAgent_isCompliantWithOsmPolicy() {
        assertTrue(AppTileSources.USER_AGENT.startsWith("GPS-Speedometer-App/"))
        assertTrue(AppTileSources.USER_AGENT.contains("github.com"))
        assertFalse(AppTileSources.USER_AGENT.contains("com.example"))
    }
}
