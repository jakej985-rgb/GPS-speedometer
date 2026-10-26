package com.example.gpsspeedometer.speedlimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `test queryIfNeeded with offlineMode avoids network calls`() = kotlinx.coroutines.runBlocking {
        val repository = SpeedLimitRepository()
        // Querying with offlineMode = true must complete without throwing network error
        repository.queryIfNeeded(37.7749, -122.4194, useMph = true, offlineMode = true)
        assertEquals(false, repository.speedLimitFlow.value.isLoading)
        assertNull(repository.speedLimitFlow.value.speedLimitMph)
    }

    @Test
    fun `test fetchRoadSpeedSegments with offlineMode returns empty without network`() = kotlinx.coroutines.runBlocking {
        val repository = SpeedLimitRepository()
        val segments = repository.fetchRoadSpeedSegments(
            south = 37.0,
            west = -122.0,
            north = 38.0,
            east = -121.0,
            zoomLevel = 15.0,
            useMph = true,
            offlineMode = true
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `test RoadSpeedSegment toEntity and toSegment serialization round trip`() {
        val original = RoadSpeedSegment(
            id = 12345L,
            name = "Broadway",
            highwayType = "primary",
            speedLimitMph = 35,
            speedLimitKmh = 56,
            coordinates = listOf(37.77 to -122.42, 37.78 to -122.43)
        )
        val entity = original.toEntity()
        assertEquals(12345L, entity.id)
        assertEquals("Broadway", entity.name)
        assertEquals(37.77, entity.minLat, 0.0001)
        assertEquals(37.78, entity.maxLat, 0.0001)
        assertEquals(-122.43, entity.minLng, 0.0001)
        assertEquals(-122.42, entity.maxLng, 0.0001)

        val restored = entity.toSegment()
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.speedLimitMph, restored.speedLimitMph)
        assertEquals(original.speedLimitKmh, restored.speedLimitKmh)
        assertEquals(original.coordinates.size, restored.coordinates.size)
        assertEquals(original.coordinates[0].first, restored.coordinates[0].first, 0.0001)
    }

    @Test
    fun `test queryIfNeeded returns local speed from OfflineSpeedDao with zero network`() = kotlinx.coroutines.runBlocking {
        val fakeDao = FakeOfflineSpeedDao()
        val entity = com.example.gpsspeedometer.data.OfflineRoadSegmentEntity(
            id = 555L,
            name = "Local Express",
            highwayType = "trunk",
            speedLimitMph = 55,
            speedLimitKmh = 88,
            minLat = 37.770,
            maxLat = 37.780,
            minLng = -122.425,
            maxLng = -122.415,
            geometrySerialized = "37.770,-122.420;37.780,-122.420"
        )
        fakeDao.insertSegments(listOf(entity))

        val repository = SpeedLimitRepository(offlineSpeedDao = fakeDao)
        repository.queryIfNeeded(37.775, -122.420, useMph = true, offlineMode = true)

        val result = repository.speedLimitFlow.value
        assertEquals(55, result.speedLimitMph)
        assertEquals(88, result.speedLimitKmh)
        assertEquals("Local Express", result.roadName)
        assertEquals(false, result.isLoading)
    }

    @Test
    fun `test fetchRoadSpeedSegments reads from OfflineSpeedDao in offlineMode`() = kotlinx.coroutines.runBlocking {
        val fakeDao = FakeOfflineSpeedDao()
        val entity = com.example.gpsspeedometer.data.OfflineRoadSegmentEntity(
            id = 888L,
            name = "Desert Highway",
            highwayType = "secondary",
            speedLimitMph = 45,
            speedLimitKmh = 72,
            minLat = 36.0,
            maxLat = 36.5,
            minLng = -115.5,
            maxLng = -115.0,
            geometrySerialized = "36.1,-115.2;36.2,-115.3"
        )
        fakeDao.insertSegments(listOf(entity))

        val repository = SpeedLimitRepository(offlineSpeedDao = fakeDao)
        val segments = repository.fetchRoadSpeedSegments(
            south = 35.9,
            west = -115.6,
            north = 36.6,
            east = -114.9,
            zoomLevel = 15.0,
            useMph = true,
            offlineMode = true
        )

        assertEquals(1, segments.size)
        assertEquals(888L, segments[0].id)
        assertEquals("Desert Highway", segments[0].name)
        assertEquals(45, segments[0].speedLimitMph)
    }

    @Test
    fun `test clearOfflineSpeedDb clears Dao storage and stats`() = kotlinx.coroutines.runBlocking {
        val fakeDao = FakeOfflineSpeedDao()
        fakeDao.insertSegments(listOf(
            com.example.gpsspeedometer.data.OfflineRoadSegmentEntity(
                id = 1L, name = "A", highwayType = "residential",
                speedLimitMph = 25, speedLimitKmh = 40,
                minLat = 10.0, maxLat = 10.1, minLng = 20.0, maxLng = 20.1,
                geometrySerialized = "10.0,20.0;10.1,20.1"
            )
        ))
        val repository = SpeedLimitRepository(offlineSpeedDao = fakeDao)
        assertEquals(1, repository.getOfflineRoadCount())

        repository.clearOfflineSpeedDb()
        assertEquals(0, repository.getOfflineRoadCount())
        assertEquals(0, fakeDao.getSegmentCount())
    }

    @Test
    fun `test parseOverpassResponse with invalid tags or missing maxspeed returns null safely`() {
        val repository = SpeedLimitRepository()
        val mockJson = """
            {
                "version": 0.6,
                "elements": [
                    {
                        "type": "way",
                        "id": 99999,
                        "tags": {
                            "highway": "unclassified",
                            "name": "Unnamed Road"
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = repository.parseOverpassResponse(mockJson, useMph = true)
        assertNull(result)
    }

    @Test
    fun `test parseOsmXml with dual carriageway inherits speed limit from sibling carriageway`() {
        val repository = SpeedLimitRepository()
        val mockOsmXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm version="0.6">
              <way id="142995074">
                <tag k="highway" v="trunk"/>
                <tag k="name" v="10th Avenue South"/>
                <tag k="maxspeed" v="35 mph"/>
              </way>
              <way id="913479990">
                <tag k="highway" v="trunk"/>
                <tag k="name" v="10th Avenue South"/>
              </way>
            </osm>
        """.trimIndent()

        val result = repository.parseOsmXml(mockOsmXml, useMph = true)
        assertNotNull(result)
        assertEquals(35, result?.speedLimitMph)
        assertEquals(56, result?.speedLimitKmh)
        assertEquals("10th Avenue South", result?.roadName)
    }

    @Test
    fun `test parseOverpassResponse with dual carriageway inherits speed limit`() {
        val repository = SpeedLimitRepository()
        val mockJson = """
            {
                "version": 0.6,
                "elements": [
                    {
                        "type": "way",
                        "id": 913479990,
                        "tags": {
                            "highway": "trunk",
                            "name": "10th Avenue South"
                        }
                    },
                    {
                        "type": "way",
                        "id": 142995074,
                        "tags": {
                            "highway": "trunk",
                            "name": "10th Avenue South",
                            "maxspeed": "35 mph"
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = repository.parseOverpassResponse(mockJson, useMph = true)
        assertNotNull(result)
        assertEquals(35, result?.speedLimitMph)
        assertEquals("10th Avenue South", result?.roadName)
    }

    @Test
    fun `test exportDiagnosticCsv exports valid CSV data`() {
        val repository = SpeedLimitRepository()
        repository.recordDiagnostic(
            SpeedLimitLookupEvent(
                lat = 47.498,
                lng = -111.285,
                currentSpeedMph = 34.5f,
                speedLimitMph = 35,
                speedLimitKmh = 56,
                roadName = "10th Avenue South",
                status = "SUCCESS",
                provider = "OsmApi",
                details = "Way 142995074 via OsmApi"
            )
        )

        val output = java.io.ByteArrayOutputStream()
        repository.exportDiagnosticCsv(output)
        val csv = output.toString("UTF-8")

        assertTrue(csv.startsWith("timestamp_iso,timestamp,latitude,longitude,speed_mph,speed_limit_mph,speed_limit_kmh,road_name,status,provider,details\n"))
        assertTrue(csv.contains("47.498,-111.285,34.5,35,56,10th Avenue South,SUCCESS,OsmApi,Way 142995074 via OsmApi"))
    }

    private class FakeOfflineSpeedDao : com.example.gpsspeedometer.data.OfflineSpeedDao {
        val storage = mutableListOf<com.example.gpsspeedometer.data.OfflineRoadSegmentEntity>()

        override suspend fun insertSegments(segments: List<com.example.gpsspeedometer.data.OfflineRoadSegmentEntity>) {
            storage.removeAll { existing -> segments.any { it.id == existing.id } }
            storage.addAll(segments)
        }

        override suspend fun getSegmentsInBoundingBox(
            south: Double,
            west: Double,
            north: Double,
            east: Double
        ): List<com.example.gpsspeedometer.data.OfflineRoadSegmentEntity> {
            return storage.filter {
                it.minLat <= north && it.maxLat >= south &&
                it.minLng <= east && it.maxLng >= west
            }
        }

        override suspend fun getSegmentNear(
            lat: Double,
            lng: Double,
            radius: Double
        ): com.example.gpsspeedometer.data.OfflineRoadSegmentEntity? {
            return storage.firstOrNull {
                it.minLat <= lat + radius && it.maxLat >= lat - radius &&
                it.minLng <= lng + radius && it.maxLng >= lng - radius
            }
        }

        override suspend fun getSegmentCount(): Int = storage.size

        override suspend fun clearAll() {
            storage.clear()
        }
    }
}
