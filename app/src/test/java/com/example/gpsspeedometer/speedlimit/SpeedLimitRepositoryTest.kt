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

    @Test
    fun `test parseRoadSegmentsResponse parses ways with coordinates and speed limits`() {
        val repository = SpeedLimitRepository()
        val mockJson = """
            {
                "version": 0.6,
                "elements": [
                    {
                        "type": "way",
                        "id": 998877,
                        "tags": {
                            "highway": "motorway",
                            "name": "Interstate 15",
                            "maxspeed": "65 mph"
                        },
                        "geometry": [
                            {"lat": 47.500, "lon": -111.300},
                            {"lat": 47.505, "lon": -111.305}
                        ]
                    },
                    {
                        "type": "way",
                        "id": 112233,
                        "tags": {
                            "highway": "residential",
                            "name": "2nd Avenue",
                            "maxspeed": "25"
                        },
                        "geometry": [
                            {"lat": 47.510, "lon": -111.310},
                            {"lat": 47.512, "lon": -111.312}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val segments = repository.parseRoadSegmentsResponse(mockJson, useMph = true)
        assertEquals(2, segments.size)

        val seg1 = segments[0]
        assertEquals(998877L, seg1.id)
        assertEquals("Interstate 15", seg1.name)
        assertEquals("motorway", seg1.highwayType)
        assertEquals(65, seg1.speedLimitMph)
        assertEquals(105, seg1.speedLimitKmh)
        assertEquals(2, seg1.coordinates.size)

        val seg2 = segments[1]
        assertEquals(112233L, seg2.id)
        assertEquals("2nd Avenue", seg2.name)
        assertEquals("residential", seg2.highwayType)
        assertEquals(25, seg2.speedLimitMph)
        assertEquals(40, seg2.speedLimitKmh)
    }
}
