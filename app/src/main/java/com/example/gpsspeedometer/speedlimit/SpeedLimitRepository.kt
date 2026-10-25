package com.example.gpsspeedometer.speedlimit

import android.location.Location
import com.example.gpsspeedometer.domain.SpeedLimitInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SpeedLimitRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    private val _speedLimitFlow = MutableStateFlow(SpeedLimitInfo())
    val speedLimitFlow: StateFlow<SpeedLimitInfo> = _speedLimitFlow.asStateFlow()

    private var lastQueriedLat: Double = 0.0
    private var lastQueriedLng: Double = 0.0
    private var lastQueryTimestamp: Long = 0L

    // In-memory cache for recent road lookups: key = quantized lat,lng grid (~100m)
    private val cache = mutableMapOf<String, SpeedLimitInfo>()
    private val queryMutex = Mutex()

    // Road segments cache: Way ID -> RoadSpeedSegment and BBox cache
    private val roadSegmentCache = mutableMapOf<Long, RoadSpeedSegment>()
    private val cachedBBoxes = mutableListOf<String>()
    private var lastRoadQueryTimestamp: Long = 0L

    suspend fun queryIfNeeded(lat: Double, lng: Double, useMph: Boolean = true, offlineMode: Boolean = false) {
        val cacheKey = getCacheKey(lat, lng)
        synchronized(cache) {
            val cached = cache[cacheKey]
            if (cached != null) {
                _speedLimitFlow.value = cached
                return
            }
        }

        // If in offline mode, do not transmit any network packets
        if (offlineMode) {
            return
        }

        val now = System.currentTimeMillis()

        // 1. Enforce minimum 15-second throttle between network calls to prevent Overpass 429 rate-limiting
        val timeElapsed = now - lastQueryTimestamp
        if (lastQueryTimestamp > 0L && timeElapsed < 15_000L) {
            return
        }

        // 2. Road segment retention: if we already have a valid speed limit and moved < 350m, retain it
        val currentInfo = _speedLimitFlow.value
        if (lastQueryTimestamp > 0L && currentInfo.speedLimitMph != null) {
            val distResults = FloatArray(1)
            Location.distanceBetween(lastQueriedLat, lastQueriedLng, lat, lng, distResults)
            val distMoved = distResults[0]
            if (distMoved < 350f && timeElapsed < 60_000L) {
                return
            }
        }

        synchronized(cache) {
            val cached = cache[cacheKey]
            if (cached != null) {
                _speedLimitFlow.value = cached
                lastQueriedLat = lat
                lastQueriedLng = lng
                lastQueryTimestamp = now
                return
            }
        }

        queryMutex.withLock {
            // Double-check under lock
            val nowLocked = System.currentTimeMillis()
            if (nowLocked - lastQueryTimestamp < 15_000L) return

            _speedLimitFlow.value = _speedLimitFlow.value.copy(isLoading = true)

            val result = fetchFromOverpass(lat, lng, useMph)
            lastQueriedLat = lat
            lastQueriedLng = lng
            lastQueryTimestamp = System.currentTimeMillis()

            if (result != null) {
                synchronized(cache) {
                    if (cache.size > 200) {
                        cache.clear()
                    }
                    cache[cacheKey] = result
                }
                _speedLimitFlow.value = result
            } else {
                _speedLimitFlow.value = _speedLimitFlow.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun fetchFromOverpass(lat: Double, lng: Double, useMph: Boolean): SpeedLimitInfo? = withContext(Dispatchers.IO) {
        try {
            // Overpass QL query around 25 meters
            val query = """[out:json][timeout:8];way(around:25.0,$lat,$lng)["highway"]["maxspeed"];out tags;"""
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://overpass-api.de/api/interpreter?data=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseOverpassResponse(body, useMph)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseOverpassResponse(json: String, useMph: Boolean = true): SpeedLimitInfo? {
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return null
            if (elements.length() == 0) return null

            // Pick the first road element with maxspeed
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "")
                if (maxspeed.isNotBlank()) {
                    val (mph, kmh) = parseMaxSpeed(maxspeed, assumeMph = useMph)
                    val roadName = tags.optString("name", tags.optString("ref", ""))
                    return SpeedLimitInfo(
                        speedLimitMph = mph,
                        speedLimitKmh = kmh,
                        roadName = if (roadName.isNotBlank()) roadName else null,
                        lastQueryTimestamp = System.currentTimeMillis(),
                        isLoading = false
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        fun getCacheKey(lat: Double, lng: Double): String {
            // Quantize to ~110m grid (3 decimal places)
            val qLat = (lat * 1000).roundToInt()
            val qLng = (lng * 1000).roundToInt()
            return "$qLat,$qLng"
        }

        fun parseMaxSpeed(raw: String, assumeMph: Boolean = true): Pair<Int?, Int?> {
            val clean = raw.trim().lowercase()
            if (clean == "none" || clean == "signals" || clean == "variable") return null to null

            val numberMatch = Regex("""\d+""").find(clean)?.value?.toIntOrNull() ?: return null to null

            return when {
                clean.contains("mph") -> {
                    val mph = numberMatch
                    val kmh = (mph * 1.609344f).roundToInt()
                    mph to kmh
                }
                clean.contains("km/h") || clean.contains("kmh") -> {
                    val kmh = numberMatch
                    val mph = (kmh / 1.609344f).roundToInt()
                    mph to kmh
                }
                clean.contains("knots") -> {
                    val knots = numberMatch
                    val mph = (knots * 1.15078f).roundToInt()
                    val kmh = (knots * 1.852f).roundToInt()
                    mph to kmh
                }
                else -> {
                    // Raw number without unit: if user is in imperial mode, OSM US tags are frequently just numbers e.g. "45"
                    if (assumeMph) {
                        val mph = numberMatch
                        val kmh = (mph * 1.609344f).roundToInt()
                        mph to kmh
                    } else {
                        val kmh = numberMatch
                        val mph = (kmh / 1.609344f).roundToInt()
                        mph to kmh
                    }
                }
            }
        }
    }

    suspend fun fetchRoadSpeedSegments(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        zoomLevel: Double,
        useMph: Boolean,
        offlineMode: Boolean = false
    ): List<RoadSpeedSegment> = withContext(Dispatchers.IO) {
        // In offline mode, serve strictly from local memory/disk cache without transmitting network packets
        if (offlineMode) {
            synchronized(roadSegmentCache) {
                return@withContext roadSegmentCache.values.filter { seg ->
                    seg.coordinates.any { (lat, lng) ->
                        lat in south..north && lng in west..east
                    }
                }
            }
        }

        // 1. Zoom gating: if zoomed out too far, don't query to avoid massive data payloads
        if (zoomLevel < 11.5) {
            return@withContext emptyList()
        }

        // Quantize bounding box to ~0.02 deg (~2km) grid for caching
        val qSouth = (south * 50).toInt() / 50.0
        val qWest = (west * 50).toInt() / 50.0
        val qNorth = (north * 50).toInt() / 50.0
        val qEast = (east * 50).toInt() / 50.0
        val zoomTier = when {
            zoomLevel < 14.0 -> "main"
            zoomLevel < 16.0 -> "major"
            else -> "all"
        }
        val bboxKey = "$zoomTier:$qSouth,$qWest,$qNorth,$qEast"

        synchronized(cachedBBoxes) {
            if (cachedBBoxes.contains(bboxKey)) {
                // Return cached segments that intersect this viewport
                synchronized(roadSegmentCache) {
                    return@withContext roadSegmentCache.values.filter { seg ->
                        seg.coordinates.any { (lat, lng) ->
                            lat in south..north && lng in west..east
                        }
                    }
                }
            }
        }

        // Throttle road network calls: minimum 4s between road batch requests
        val now = System.currentTimeMillis()
        if (now - lastRoadQueryTimestamp < 4000L) {
            synchronized(roadSegmentCache) {
                return@withContext roadSegmentCache.values.filter { seg ->
                    seg.coordinates.any { (lat, lng) ->
                        lat in south..north && lng in west..east
                    }
                }
            }
        }

        val highwayFilter = when {
            zoomLevel < 14.0 -> """["highway"~"^(motorway|trunk|primary|motorway_link|trunk_link|primary_link)$"]"""
            zoomLevel < 16.0 -> """["highway"~"^(motorway|trunk|primary|secondary|tertiary|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"]"""
            else -> """["highway"]"""
        }

        val query = """[out:json][timeout:12];way$highwayFilter["maxspeed"]($south,$west,$north,$east);out geom;"""
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://overpass-api.de/api/interpreter?data=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                .build()

            lastRoadQueryTimestamp = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    synchronized(roadSegmentCache) {
                        return@withContext roadSegmentCache.values.filter { seg ->
                            seg.coordinates.any { (lat, lng) ->
                                lat in south..north && lng in west..east
                            }
                        }
                    }
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = parseRoadSegmentsResponse(body, useMph)

                synchronized(cachedBBoxes) {
                    if (cachedBBoxes.size > 50) cachedBBoxes.removeAt(0)
                    cachedBBoxes.add(bboxKey)
                }

                synchronized(roadSegmentCache) {
                    for (seg in parsed) {
                        roadSegmentCache[seg.id] = seg
                    }
                    if (roadSegmentCache.size > 2000) {
                        // Evict oldest entries
                        val keysToRemove = roadSegmentCache.keys.take(500)
                        keysToRemove.forEach { roadSegmentCache.remove(it) }
                    }
                    return@withContext roadSegmentCache.values.filter { seg ->
                        seg.coordinates.any { (lat, lng) ->
                            lat in south..north && lng in west..east
                        }
                    }
                }
            }
        } catch (e: Exception) {
            synchronized(roadSegmentCache) {
                roadSegmentCache.values.filter { seg ->
                    seg.coordinates.any { (lat, lng) ->
                        lat in south..north && lng in west..east
                    }
                }
            }
        }
    }

    fun parseRoadSegmentsResponse(json: String, useMph: Boolean = true): List<RoadSpeedSegment> {
        val result = mutableListOf<RoadSpeedSegment>()
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            for (i in 0 until elements.length()) {
                val elem = elements.getJSONObject(i)
                val id = elem.optLong("id")
                val tags = elem.optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "")
                if (maxspeed.isBlank()) continue

                val (mph, kmh) = parseMaxSpeed(maxspeed, assumeMph = useMph)
                if (mph == null || kmh == null) continue

                val highway = tags.optString("highway", "road")
                val name = tags.optString("name", tags.optString("ref", "")).ifBlank { null }
                val geom = elem.optJSONArray("geometry") ?: continue
                val coords = mutableListOf<Pair<Double, Double>>()
                for (j in 0 until geom.length()) {
                    val pt = geom.getJSONObject(j)
                    coords.add(pt.getDouble("lat") to pt.getDouble("lon"))
                }
                if (coords.size >= 2) {
                    result.add(RoadSpeedSegment(id, name, highway, mph, kmh, coords))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}

data class RoadSpeedSegment(
    val id: Long,
    val name: String?,
    val highwayType: String,
    val speedLimitMph: Int,
    val speedLimitKmh: Int,
    val coordinates: List<Pair<Double, Double>>
)

