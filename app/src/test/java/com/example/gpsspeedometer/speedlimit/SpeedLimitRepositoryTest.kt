package com.example.gpsspeedometer.speedlimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitRepositoryTest {

    @Test
    fun `test parseMaxSpeed with mph suffix`() {
        val (mph, kmh) = SpeedLimitRepository.parseMaxSpeed("45 mph")
        assertEquals(45, mph)
        assertEquals(72, kmh)
    }

    @Test
    fun `test parseMaxSpeed with kmh suffix`() {
        val (mph, kmh) = SpeedLimitRepository.parseMaxSpeed("80 km/h")
        assertEquals(50, mph)
        assertEquals(80, kmh)
    }

    @Test
    fun `test parseMaxSpeed with raw number assume mph`() {
        val (mph, kmh) = SpeedLimitRepository.parseMaxSpeed("65", assumeMph = true)
        assertEquals(65, mph)
        assertEquals(105, kmh)
    }

    @Test
    fun `test parseMaxSpeed with raw number assume kmh`() {
        val (mph, kmh) = SpeedLimitRepository.parseMaxSpeed("100", assumeMph = false)
        assertEquals(62, mph)
        assertEquals(100, kmh)
    }

    @Test
    fun `test parseMaxSpeed with none or invalid returns null`() {
        val (mph1, _) = SpeedLimitRepository.parseMaxSpeed("none")
        assertNull(mph1)

        val (mph2, _) = SpeedLimitRepository.parseMaxSpeed("signals")
        assertNull(mph2)

        val (mph3, _) = SpeedLimitRepository.parseMaxSpeed("")
        assertNull(mph3)
    }

    @Test
    fun `test parseOverpassResponse extracts speed limit and road name`() {
        val repository = SpeedLimitRepository()
        val mockJson = """
            {
                "version": 0.6,
                "elements": [
                    {
                        "type": "way",
                        "id": 123456,
                        "tags": {
                            "highway": "primary",
                            "name": "Oak Street",
                            "maxspeed": "35 mph"
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = repository.parseOverpassResponse(mockJson, useMph = true)
        assertNotNull(result)
        assertEquals(35, result?.speedLimitMph)
        assertEquals(56, result?.speedLimitKmh)
        assertEquals("Oak Street", result?.roadName)
    }

    @Test
    fun `test parseOverpassResponse with empty elements returns null`() {
        val repository = SpeedLimitRepository()
        val mockJson = """
            {
                "version": 0.6,
                "elements": []
            }
        """.trimIndent()

        val result = repository.parseOverpassResponse(mockJson, useMph = true)
        assertNull(result)
    }

    @Test
    fun `test getCacheKey quantizes nearby coordinates`() {
        val key1 = SpeedLimitRepository.getCacheKey(37.774929, -122.419416)
        val key2 = SpeedLimitRepository.getCacheKey(37.774940, -122.419420)
        assertEquals(key1, key2)
    }
}
