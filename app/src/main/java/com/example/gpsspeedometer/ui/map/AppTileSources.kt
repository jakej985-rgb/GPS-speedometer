package com.example.gpsspeedometer.ui.map

import android.content.Context
import android.util.Log
import com.example.gpsspeedometer.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File

object AppTileSources {

    const val USER_AGENT = "GPS-Speedometer-App/${BuildConfig.VERSION_NAME} (+https://github.com/jakej985-rgb/GPS-speedometer; contact: jakej985@gmail.com; Android)"

    /**
     * Standard OpenStreetMap Carto tile source strictly adhering to OSM Tile Usage Policy.
     * Note: We explicitly do NOT include FLAG_USER_AGENT_NORMALIZED so osmdroid will NOT
     * replace our compliant application User-Agent with the internal package name (com.example.*).
     */
    val OpenStreetMap: OnlineTileSourceBase = object : XYTileSource(
        "OpenStreetMapStandard",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://tile.openstreetmap.org/"
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
        )
    ) {}

    /**
     * Configure osmdroid globally to guarantee compliance with tile usage policies,
     * eliminate blocked 403 cached error tiles, and ensure compliant User-Agent headers.
     */
    fun setupOsmdroid(context: Context) {
        try {
            val prefs = context.getSharedPreferences("${context.packageName}_osm", Context.MODE_PRIVATE)
            val osmConfig = Configuration.getInstance()
            val basePath = File(context.cacheDir, "osmdroid")
            if (!basePath.exists()) basePath.mkdirs()
            osmConfig.osmdroidBasePath = basePath

            // Switch to tiles_v3 to completely bypass any legacy cached 403 blocked tiles
            val tilesDir = File(basePath, "tiles_v3")
            if (!tilesDir.exists()) {
                tilesDir.mkdirs()
            }

            // Remove legacy cache folders containing blocked 403 tiles
            listOf("tiles", "tiles_v2").forEach { oldDirName ->
                try {
                    val oldDir = File(basePath, oldDirName)
                    if (oldDir.exists()) oldDir.deleteRecursively()
                } catch (e: Exception) {
                    Log.w("AppTileSources", "Could not purge old cache directory: $oldDirName", e)
                }
            }

            osmConfig.osmdroidTileCache = tilesDir
            osmConfig.load(context, prefs)

            // Ensure osmdroid sends our compliant User-Agent
            osmConfig.userAgentValue = USER_AGENT
            osmConfig.userAgentHttpHeader = "User-Agent"

            // Override private mNormalizedUserAgent via reflection so that even if any
            // component queries getNormalizedUserAgent(), it NEVER returns com.example.*
            try {
                val field = osmConfig.javaClass.getDeclaredField("mNormalizedUserAgent")
                field.isAccessible = true
                field.set(osmConfig, USER_AGENT)
            } catch (e: Exception) {
                Log.w("AppTileSources", "mNormalizedUserAgent reflection override skipped", e)
            }

            osmConfig.save(context, prefs)
        } catch (e: Exception) {
            Log.e("AppTileSources", "Error configuring osmdroid", e)
        }
    }
}
