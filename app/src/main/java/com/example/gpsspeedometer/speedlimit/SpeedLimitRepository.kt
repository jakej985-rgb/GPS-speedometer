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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.example.gpsspeedometer.data.OfflineRoadSegmentEntity
import com.example.gpsspeedometer.data.OfflineSpeedDao
import java.io.ByteArrayInputStream
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node

data class SpeedLimitLookupEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val lat: Double,
    val lng: Double,
    val currentSpeedMph: Float = 0f,
    val speedLimitMph: Int? = null,
    val speedLimitKmh: Int? = null,
    val roadName: String? = null,
    val status: String,
    val provider: String,
    val details: String
)

class SpeedLimitRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val offlineSpeedDao: OfflineSpeedDao? = null
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

    // In-memory diagnostic log of speed limit lookups (last 200 events)
    private val _diagnosticEvents = mutableListOf<SpeedLimitLookupEvent>()
    val diagnosticEvents: List<SpeedLimitLookupEvent>
        get() = synchronized(_diagnosticEvents) { _diagnosticEvents.toList() }

    internal fun recordDiagnostic(event: SpeedLimitLookupEvent) {
        synchronized(_diagnosticEvents) {
            if (_diagnosticEvents.size >= 200) {
                _diagnosticEvents.removeAt(0)
            }
            _diagnosticEvents.add(event)
        }
    }

    suspend fun queryIfNeeded(
        lat: Double,
        lng: Double,
        useMph: Boolean = true,
        offlineMode: Boolean = false,
        currentSpeedMph: Float = 0f
    ) {
        val cacheKey = getCacheKey(lat, lng)
        synchronized(cache) {
            val cached = cache[cacheKey]
            if (cached != null) {
                _speedLimitFlow.value = cached
                return
            }
        }

        // 1. Check local persistent offline speed DB
        try {
            val localSegment = offlineSpeedDao?.getSegmentNear(lat, lng)
            if (localSegment != null) {
                val info = SpeedLimitInfo(
                    speedLimitMph = localSegment.speedLimitMph,
                    speedLimitKmh = localSegment.speedLimitKmh,
                    roadName = localSegment.name,
                    lastQueryTimestamp = System.currentTimeMillis(),
                    isLoading = false
                )
                synchronized(cache) { cache[cacheKey] = info }
                _speedLimitFlow.value = info
                recordDiagnostic(
                    SpeedLimitLookupEvent(
                        lat = lat,
                        lng = lng,
                        currentSpeedMph = currentSpeedMph,
                        speedLimitMph = localSegment.speedLimitMph,
                        speedLimitKmh = localSegment.speedLimitKmh,
                        roadName = localSegment.name,
                        status = "OFFLINE_CACHE",
                        provider = "OfflineRoomDb",
                        details = "Resolved from local on-device database"
                    )
                )
                return
            }
        } catch (e: Exception) {
            // Room query error, fallback to network
        }

        // If in offline mode and not in local DB, do not transmit any network packets
        if (offlineMode) {
            recordDiagnostic(
                SpeedLimitLookupEvent(
                    lat = lat,
                    lng = lng,
                    currentSpeedMph = currentSpeedMph,
                    status = "OFFLINE_MODE",
                    provider = "OfflineRoomDb",
                    details = "Offline mode active; network requests blocked"
                )
            )
            return
        }

        val now = System.currentTimeMillis()

        // Enforce minimum 10-second throttle between network calls
        val timeElapsed = now - lastQueryTimestamp
        if (lastQueryTimestamp > 0L && timeElapsed < 10_000L) {
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
            val nowLocked = System.currentTimeMillis()
            if (nowLocked - lastQueryTimestamp < 10_000L) return

            _speedLimitFlow.value = _speedLimitFlow.value.copy(isLoading = true)

            // Try primary provider: api.openstreetmap.org 0.6 Map API (fast, 99.99% uptime, no 502/406)
            var result = fetchFromOsmApi(lat, lng, useMph)
            var usedProvider = "OpenStreetMap-0.6"

            // Fallback to Overpass API mirrors if OSM API returned null
            if (result == null || result.speedLimitMph == null) {
                val overpassResult = fetchFromOverpass(lat, lng, useMph)
                if (overpassResult != null && overpassResult.speedLimitMph != null) {
                    result = overpassResult
                    usedProvider = "Overpass"
                }
            }

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
                recordDiagnostic(
                    SpeedLimitLookupEvent(
                        lat = lat,
                        lng = lng,
                        currentSpeedMph = currentSpeedMph,
                        speedLimitMph = result.speedLimitMph,
                        speedLimitKmh = result.speedLimitKmh,
                        roadName = result.roadName,
                        status = if (result.speedLimitMph != null) "SUCCESS" else "NO_SPEED_TAG",
                        provider = usedProvider,
                        details = if (result.speedLimitMph != null) {
                            "Matched road: ${result.roadName ?: "Unnamed"} (${result.speedLimitMph} mph)"
                        } else {
                            "Road found (${result.roadName ?: "Unnamed"}) but has no speed limit tag"
                        }
                    )
                )
            } else {
                _speedLimitFlow.value = _speedLimitFlow.value.copy(isLoading = false)
                recordDiagnostic(
                    SpeedLimitLookupEvent(
                        lat = lat,
                        lng = lng,
                        currentSpeedMph = currentSpeedMph,
                        status = "NO_ROADS_FOUND",
                        provider = "OSM/Overpass",
                        details = "No drivable road found within search radius"
                    )
                )
            }
        }
    }

    private suspend fun fetchFromOsmApi(lat: Double, lng: Double, useMph: Boolean): SpeedLimitInfo? = withContext(Dispatchers.IO) {
        val dlat = 0.0007
        val dlng = 0.0010
        val bbox = "${lng - dlng},${lat - dlat},${lng + dlng},${lat + dlat}"
        val url = "https://api.openstreetmap.org/api/0.6/map?bbox=$bbox"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                .header("Accept", "application/xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        return@withContext parseOsmXml(body, useMph)
                    }
                }
            }
        } catch (e: Exception) {
            // Network failure on OSM API, let caller try Overpass fallback
        }
        null
    }

    fun parseOsmXml(xml: String, useMph: Boolean = true): SpeedLimitInfo? {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val wayNodes = doc.getElementsByTagName("way")

            data class OsmCandidate(val id: Long, val name: String?, val ref: String?, val highway: String, val maxspeed: String?)
            val candidates = mutableListOf<OsmCandidate>()
            val ignoredHighways = setOf("footway", "cycleway", "path", "steps", "pedestrian", "service", "track")

            for (i in 0 until wayNodes.length) {
                val wayNode = wayNodes.item(i)
                if (wayNode.nodeType != Node.ELEMENT_NODE) continue
                val wayElement = wayNode as Element
                val wayId = wayElement.getAttribute("id").toLongOrNull() ?: 0L

                var highway: String? = null
                var name: String? = null
                var ref: String? = null
                var maxspeed: String? = null

                val tagNodes = wayElement.getElementsByTagName("tag")
                for (j in 0 until tagNodes.length) {
                    val tagNode = tagNodes.item(j)
                    if (tagNode.nodeType != Node.ELEMENT_NODE) continue
                    val tagElement = tagNode as Element
                    when (tagElement.getAttribute("k")) {
                        "highway" -> highway = tagElement.getAttribute("v")
                        "name" -> name = tagElement.getAttribute("v")
                        "ref" -> ref = tagElement.getAttribute("v")
                        "maxspeed" -> maxspeed = tagElement.getAttribute("v")
                    }
                }

                if (highway != null && highway !in ignoredHighways) {
                    candidates.add(OsmCandidate(wayId, name, ref, highway, maxspeed))
                }
            }

            if (candidates.isEmpty()) return null

            // Dual carriageway / divided road clustering:
            // Sibling ways sharing the same name or ref inherit explicit maxspeed
            val speedByRoadName = mutableMapOf<String, String>()
            for (c in candidates) {
                if (!c.maxspeed.isNullOrBlank()) {
                    c.name?.let { speedByRoadName[it.lowercase()] = c.maxspeed }
                    c.ref?.let { speedByRoadName[it.lowercase()] = c.maxspeed }
                }
            }

            val hierarchy = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential")

            val withSpeed = candidates.filter {
                !it.maxspeed.isNullOrBlank() ||
                (it.name != null && speedByRoadName.containsKey(it.name.lowercase())) ||
                (it.ref != null && speedByRoadName.containsKey(it.ref.lowercase()))
            }.sortedBy { c ->
                val idx = hierarchy.indexOf(c.highway)
                if (idx >= 0) idx else 99
            }

            if (withSpeed.isNotEmpty()) {
                val best = withSpeed.first()
                val speedRaw = best.maxspeed
                    ?: best.name?.let { speedByRoadName[it.lowercase()] }
                    ?: best.ref?.let { speedByRoadName[it.lowercase()] }

                if (speedRaw != null) {
                    val (mph, kmh) = parseMaxSpeed(speedRaw, assumeMph = useMph)
                    return SpeedLimitInfo(
                        speedLimitMph = mph,
                        speedLimitKmh = kmh,
                        roadName = best.name ?: best.ref,
                        lastQueryTimestamp = System.currentTimeMillis(),
                        isLoading = false
                    )
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun fetchFromOverpass(lat: Double, lng: Double, useMph: Boolean): SpeedLimitInfo? = withContext(Dispatchers.IO) {
        val query = """[out:json][timeout:8];way(around:65.0,$lat,$lng)["highway"];out tags;"""
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )

        for (url in endpoints) {
            try {
                val formBody = FormBody.Builder()
                    .add("data", query)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                    .header("Referer", "https://github.com/jakej985-rgb/GPS-speedometer")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseOverpassResponse(body, useMph)
                            if (parsed != null) return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        null
    }

    fun parseOverpassResponse(json: String, useMph: Boolean = true): SpeedLimitInfo? {
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return null
            if (elements.length() == 0) return null

            data class OverpassCandidate(val name: String?, val ref: String?, val highway: String, val maxspeed: String?)
            val candidates = mutableListOf<OverpassCandidate>()
            val ignoredHighways = setOf("footway", "cycleway", "path", "steps", "pedestrian", "service", "track")

            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags") ?: continue
                val highway = tags.optString("highway", "")
                if (highway.isNotBlank() && highway !in ignoredHighways) {
                    val name = tags.optString("name", "").ifBlank { null }
                    val ref = tags.optString("ref", "").ifBlank { null }
                    val maxspeed = tags.optString("maxspeed", "").ifBlank { null }
                    candidates.add(OverpassCandidate(name, ref, highway, maxspeed))
                }
            }

            if (candidates.isEmpty()) return null

            // Dual carriageway speed limit inheritance
            val speedByRoadName = mutableMapOf<String, String>()
            for (c in candidates) {
                if (c.maxspeed != null) {
                    c.name?.let { speedByRoadName[it.lowercase()] = c.maxspeed }
                    c.ref?.let { speedByRoadName[it.lowercase()] = c.maxspeed }
                }
            }

            val hierarchy = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential")

            val withSpeed = candidates.filter {
                it.maxspeed != null ||
                (it.name != null && speedByRoadName.containsKey(it.name.lowercase())) ||
                (it.ref != null && speedByRoadName.containsKey(it.ref.lowercase()))
            }.sortedBy { c ->
                val idx = hierarchy.indexOf(c.highway)
                if (idx >= 0) idx else 99
            }

            if (withSpeed.isNotEmpty()) {
                val best = withSpeed.first()
                val speedRaw = best.maxspeed
                    ?: best.name?.let { speedByRoadName[it.lowercase()] }
                    ?: best.ref?.let { speedByRoadName[it.lowercase()] }

                if (speedRaw != null) {
                    val (mph, kmh) = parseMaxSpeed(speedRaw, assumeMph = useMph)
                    return SpeedLimitInfo(
                        speedLimitMph = mph,
                        speedLimitKmh = kmh,
                        roadName = best.name ?: best.ref,
                        lastQueryTimestamp = System.currentTimeMillis(),
                        isLoading = false
                    )
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    fun exportDiagnosticCsv(outputStream: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream))
        writer.write("timestamp_iso,timestamp,latitude,longitude,speed_mph,speed_limit_mph,speed_limit_kmh,road_name,status,provider,details\n")
        val events = diagnosticEvents
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (event in events) {
            val iso = isoFormat.format(Date(event.timestamp))
            val road = escapeCsv(event.roadName ?: "")
            val det = escapeCsv(event.details)
            writer.write("$iso,${event.timestamp},${event.lat},${event.lng},${event.currentSpeedMph},${event.speedLimitMph ?: ""},${event.speedLimitKmh ?: ""},$road,${event.status},${event.provider},$det\n")
        }
        writer.flush()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
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
        // 1. Check local persistent offline speed DB
        val localEntities = try {
            offlineSpeedDao?.getSegmentsInBoundingBox(south, west, north, east) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (localEntities.isNotEmpty()) {
            val localSegments = localEntities.map { it.toSegment() }
            synchronized(roadSegmentCache) {
                for (seg in localSegments) {
                    roadSegmentCache[seg.id] = seg
                }
            }
            if (offlineMode) {
                return@withContext localSegments
            }
        }

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

        // 2. Zoom gating: if zoomed out too far, don't query to avoid massive data payloads
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

        // Throttle road network calls: minimum 2.5s between road batch requests
        val now = System.currentTimeMillis()
        if (now - lastRoadQueryTimestamp < 2500L) {
            synchronized(roadSegmentCache) {
                val cached = roadSegmentCache.values.filter { seg ->
                    seg.coordinates.any { (lat, lng) ->
                        lat in south..north && lng in west..east
                    }
                }
                if (cached.isNotEmpty()) return@withContext cached
            }
        }

        var fetchedSegments = emptyList<RoadSpeedSegment>()

        // 3. Primary provider: OpenStreetMap Foundation REST API
        val dLat = kotlin.math.abs(north - south)
        val dLng = kotlin.math.abs(east - west)
        if (dLat <= 0.15 && dLng <= 0.15) {
            try {
                val minLat = kotlin.math.min(south, north)
                val maxLat = kotlin.math.max(south, north)
                val minLng = kotlin.math.min(west, east)
                val maxLng = kotlin.math.max(west, east)
                val url = "https://api.openstreetmap.org/api/0.6/map?bbox=$minLng,$minLat,$maxLng,$maxLat"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                    .header("Accept", "application/xml")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            fetchedSegments = parseOsmXmlRoadSegments(body, useMph)
                        }
                    }
                }
            } catch (e: Exception) {
                // Network error on OSM API, fallback to Overpass
            }
        }

        // 4. Fallback provider: Overpass POST across multiple mirrors
        if (fetchedSegments.isEmpty()) {
            val highwayFilter = when {
                zoomLevel < 14.0 -> """["highway"~"^(motorway|trunk|primary|motorway_link|trunk_link|primary_link)$"]"""
                zoomLevel < 16.0 -> """["highway"~"^(motorway|trunk|primary|secondary|tertiary|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"]"""
                else -> """["highway"]"""
            }
            val query = """[out:json][timeout:15];way$highwayFilter["maxspeed"]($south,$west,$north,$east);out geom;"""
            val endpoints = listOf(
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass.private.coffee/api/interpreter",
                "https://overpass-api.de/api/interpreter"
            )

            for (endpoint in endpoints) {
                try {
                    val formBody = FormBody.Builder().add("data", query).build()
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(formBody)
                        .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                        .header("Referer", "https://github.com/jakej985-rgb/GPS-speedometer")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                fetchedSegments = parseRoadSegmentsResponse(body, useMph)
                            }
                        }
                    }
                    if (fetchedSegments.isNotEmpty()) break
                } catch (e: Exception) {
                    // Try next Overpass endpoint
                }
            }
        }

        lastRoadQueryTimestamp = System.currentTimeMillis()

        if (fetchedSegments.isNotEmpty()) {
            try {
                offlineSpeedDao?.insertSegments(fetchedSegments.map { it.toEntity() })
            } catch (e: Exception) {
                // SQLite insertion error
            }

            synchronized(cachedBBoxes) {
                if (cachedBBoxes.size > 50) cachedBBoxes.removeAt(0)
                cachedBBoxes.add(bboxKey)
            }

            synchronized(roadSegmentCache) {
                for (seg in fetchedSegments) {
                    roadSegmentCache[seg.id] = seg
                }
                if (roadSegmentCache.size > 2000) {
                    // Evict oldest entries
                    val keysToRemove = roadSegmentCache.keys.take(500)
                    keysToRemove.forEach { roadSegmentCache.remove(it) }
                }
            }
        }

        synchronized(roadSegmentCache) {
            return@withContext roadSegmentCache.values.filter { seg ->
                seg.coordinates.any { (lat, lng) ->
                    lat in south..north && lng in west..east
                }
            }
        }
    }

    suspend fun downloadAreaSpeedLimits(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        useMph: Boolean
    ): Int = withContext(Dispatchers.IO) {
        val dLat = kotlin.math.abs(north - south)
        val dLng = kotlin.math.abs(east - west)
        var parsed = emptyList<RoadSpeedSegment>()

        // 1. Try OSM API if area is within 0.20 degrees (~20km)
        if (dLat <= 0.20 && dLng <= 0.20) {
            try {
                val minLat = kotlin.math.min(south, north)
                val maxLat = kotlin.math.max(south, north)
                val minLng = kotlin.math.min(west, east)
                val maxLng = kotlin.math.max(west, east)
                val url = "https://api.openstreetmap.org/api/0.6/map?bbox=$minLng,$minLat,$maxLng,$maxLat"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                    .header("Accept", "application/xml")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            parsed = parseOsmXmlRoadSegments(body, useMph)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to Overpass
            }
        }

        // 2. Fallback to Overpass POST across mirrors
        if (parsed.isEmpty()) {
            val query = """[out:json][timeout:25];way["highway"]["maxspeed"]($south,$west,$north,$east);out geom;"""
            val endpoints = listOf(
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass.private.coffee/api/interpreter",
                "https://overpass-api.de/api/interpreter"
            )

            for (endpoint in endpoints) {
                try {
                    val formBody = FormBody.Builder().add("data", query).build()
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(formBody)
                        .header("User-Agent", "GPS-Speedometer-App/1.0 (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)")
                        .header("Referer", "https://github.com/jakej985-rgb/GPS-speedometer")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                parsed = parseRoadSegmentsResponse(body, useMph)
                            }
                        }
                    }
                    if (parsed.isNotEmpty()) break
                } catch (e: Exception) {
                    // Try next mirror
                }
            }
        }

        if (parsed.isNotEmpty()) {
            try {
                offlineSpeedDao?.insertSegments(parsed.map { it.toEntity() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
            synchronized(roadSegmentCache) {
                for (seg in parsed) {
                    roadSegmentCache[seg.id] = seg
                }
            }
        }
        return@withContext parsed.size
    }

    suspend fun getOfflineRoadCount(): Int = withContext(Dispatchers.IO) {
        try {
            offlineSpeedDao?.getSegmentCount() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun clearOfflineSpeedDb() = withContext(Dispatchers.IO) {
        try {
            offlineSpeedDao?.clearAll()
            synchronized(roadSegmentCache) {
                roadSegmentCache.clear()
            }
            synchronized(cachedBBoxes) {
                cachedBBoxes.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseOsmXmlRoadSegments(xml: String, useMph: Boolean = true): List<RoadSpeedSegment> {
        val segments = mutableListOf<RoadSpeedSegment>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

            val nodeNodes = doc.getElementsByTagName("node")
            val nodeCoords = mutableMapOf<Long, Pair<Double, Double>>()
            for (i in 0 until nodeNodes.length) {
                val nodeItem = nodeNodes.item(i)
                if (nodeItem.nodeType != Node.ELEMENT_NODE) continue
                val elem = nodeItem as Element
                val id = elem.getAttribute("id").toLongOrNull() ?: continue
                val lat = elem.getAttribute("lat").toDoubleOrNull() ?: continue
                val lon = elem.getAttribute("lon").toDoubleOrNull() ?: continue
                nodeCoords[id] = lat to lon
            }

            val wayNodes = doc.getElementsByTagName("way")
            data class RawOsmWay(
                val id: Long,
                val name: String?,
                val ref: String?,
                val highway: String,
                val maxspeed: String?,
                val nodeRefs: List<Long>
            )
            val rawWays = mutableListOf<RawOsmWay>()
            val ignoredHighways = setOf("footway", "cycleway", "path", "steps", "pedestrian", "service", "track")
            val speedByRoadName = mutableMapOf<String, String>()

            for (i in 0 until wayNodes.length) {
                val wayItem = wayNodes.item(i)
                if (wayItem.nodeType != Node.ELEMENT_NODE) continue
                val elem = wayItem as Element
                val id = elem.getAttribute("id").toLongOrNull() ?: continue

                var highway: String? = null
                var name: String? = null
                var ref: String? = null
                var maxspeed: String? = null

                val tagNodes = elem.getElementsByTagName("tag")
                for (j in 0 until tagNodes.length) {
                    val tagItem = tagNodes.item(j)
                    if (tagItem.nodeType != Node.ELEMENT_NODE) continue
                    val tagElem = tagItem as Element
                    when (tagElem.getAttribute("k")) {
                        "highway" -> highway = tagElem.getAttribute("v")
                        "name" -> name = tagElem.getAttribute("v")
                        "ref" -> ref = tagElem.getAttribute("v")
                        "maxspeed" -> maxspeed = tagElem.getAttribute("v")
                    }
                }

                if (highway.isNullOrBlank() || highway in ignoredHighways) continue

                val ndNodes = elem.getElementsByTagName("nd")
                val nodeRefs = mutableListOf<Long>()
                for (j in 0 until ndNodes.length) {
                    val ndItem = ndNodes.item(j)
                    if (ndItem.nodeType != Node.ELEMENT_NODE) continue
                    val ndElem = ndItem as Element
                    ndElem.getAttribute("ref").toLongOrNull()?.let { nodeRefs.add(it) }
                }

                rawWays.add(RawOsmWay(id, name, ref, highway, maxspeed, nodeRefs))
                if (!maxspeed.isNullOrBlank()) {
                    name?.let { speedByRoadName[it.lowercase()] = maxspeed }
                    ref?.let { speedByRoadName[it.lowercase()] = maxspeed }
                }
            }

            for (w in rawWays) {
                val speedRaw = w.maxspeed
                    ?: w.name?.let { speedByRoadName[it.lowercase()] }
                    ?: w.ref?.let { speedByRoadName[it.lowercase()] }
                    ?: continue

                val (mph, kmh) = parseMaxSpeed(speedRaw, assumeMph = useMph)
                if (mph == null || kmh == null) continue

                val coords = w.nodeRefs.mapNotNull { nodeCoords[it] }
                if (coords.size >= 2) {
                    segments.add(
                        RoadSpeedSegment(
                            id = w.id,
                            name = w.name ?: w.ref,
                            highwayType = w.highway,
                            speedLimitMph = mph,
                            speedLimitKmh = kmh,
                            coordinates = coords
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return segments
    }

    fun parseRoadSegmentsResponse(json: String, useMph: Boolean = true): List<RoadSpeedSegment> {
        val result = mutableListOf<RoadSpeedSegment>()
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return emptyList()

            data class JsonWay(
                val id: Long,
                val name: String?,
                val ref: String?,
                val highway: String,
                val maxspeed: String?,
                val coords: List<Pair<Double, Double>>
            )
            val jsonWays = mutableListOf<JsonWay>()
            val speedByName = mutableMapOf<String, String>()

            for (i in 0 until elements.length()) {
                val elem = elements.getJSONObject(i)
                val id = elem.optLong("id")
                val tags = elem.optJSONObject("tags") ?: continue
                val highway = tags.optString("highway", "")
                if (highway.isBlank()) continue
                val maxspeed = tags.optString("maxspeed", "").ifBlank { null }
                val name = tags.optString("name", "").ifBlank { null }
                val ref = tags.optString("ref", "").ifBlank { null }
                val geom = elem.optJSONArray("geometry") ?: continue
                val coords = mutableListOf<Pair<Double, Double>>()
                for (j in 0 until geom.length()) {
                    val pt = geom.getJSONObject(j)
                    coords.add(pt.getDouble("lat") to pt.getDouble("lon"))
                }
                if (coords.size >= 2) {
                    jsonWays.add(JsonWay(id, name, ref, highway, maxspeed, coords))
                    if (maxspeed != null) {
                        name?.let { speedByName[it.lowercase()] = maxspeed }
                        ref?.let { speedByName[it.lowercase()] = maxspeed }
                    }
                }
            }

            for (w in jsonWays) {
                val speedRaw = w.maxspeed
                    ?: w.name?.let { speedByName[it.lowercase()] }
                    ?: w.ref?.let { speedByName[it.lowercase()] }
                    ?: continue

                val (mph, kmh) = parseMaxSpeed(speedRaw, assumeMph = useMph)
                if (mph != null && kmh != null) {
                    result.add(RoadSpeedSegment(w.id, w.name ?: w.ref, w.highway, mph, kmh, w.coords))
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

fun RoadSpeedSegment.toEntity(): OfflineRoadSegmentEntity {
    val minLat = coordinates.minOf { it.first }
    val maxLat = coordinates.maxOf { it.first }
    val minLng = coordinates.minOf { it.second }
    val maxLng = coordinates.maxOf { it.second }
    val geomStr = coordinates.joinToString(";") { "${it.first},${it.second}" }
    return OfflineRoadSegmentEntity(
        id = id,
        name = name,
        highwayType = highwayType,
        speedLimitMph = speedLimitMph,
        speedLimitKmh = speedLimitKmh,
        minLat = minLat,
        maxLat = maxLat,
        minLng = minLng,
        maxLng = maxLng,
        geometrySerialized = geomStr
    )
}

fun OfflineRoadSegmentEntity.toSegment(): RoadSpeedSegment {
    val coords = geometrySerialized.split(";").mapNotNull { pairStr ->
        val parts = pairStr.split(",")
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null) lat to lng else null
        } else null
    }
    return RoadSpeedSegment(
        id = id,
        name = name,
        highwayType = highwayType,
        speedLimitMph = speedLimitMph,
        speedLimitKmh = speedLimitKmh,
        coordinates = coords
    )
}


