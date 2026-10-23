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

    suspend fun queryIfNeeded(lat: Double, lng: Double, useMph: Boolean = true) {
        val now = System.currentTimeMillis()

        // Check throttle: if within 100m and less than 30s elapsed, skip network query
        if (lastQueryTimestamp > 0L) {
            val distResults = FloatArray(1)
            Location.distanceBetween(lastQueriedLat, lastQueriedLng, lat, lng, distResults)
            val distMoved = distResults[0]
            val timeElapsed = now - lastQueryTimestamp

            if (distMoved < 100f && timeElapsed < 30_000L) {
                return
            }
        }

        val cacheKey = getCacheKey(lat, lng)
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
            // Double-check after acquiring lock
            if (now - lastQueryTimestamp < 2000L) return

            _speedLimitFlow.value = _speedLimitFlow.value.copy(isLoading = true)

            val result = fetchFromOverpass(lat, lng, useMph)
            lastQueriedLat = lat
            lastQueriedLng = lng
            lastQueryTimestamp = System.currentTimeMillis()

            if (result != null) {
                synchronized(cache) {
                    if (cache.size > 50) {
                        cache.clear() // Simple LRU / eviction
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
                .header("User-Agent", "GPS-Speedometer-Android/1.0 (open-source; Android)")
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
}
