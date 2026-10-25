package com.example.gpsspeedometer.ui.map

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream

class OfflineMapManager(private val context: Context) {

    fun downloadAreaTiles(
        mapView: MapView,
        bbox: BoundingBox,
        minZoom: Int = 13,
        maxZoom: Int = 17,
        onProgress: (percent: Int) -> Unit,
        onComplete: (totalTiles: Int) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            val cacheManager = CacheManager(mapView)
            val initialTotal = cacheManager.possibleTilesInArea(bbox, minZoom, maxZoom)
            if (initialTotal <= 0) {
                onComplete(0)
                return
            }

            cacheManager.downloadAreaAsync(
                context,
                bbox,
                minZoom,
                maxZoom,
                object : CacheManager.CacheManagerCallback {
                    private var totalTiles = initialTotal

                    override fun onTaskComplete() {
                        onComplete(totalTiles)
                    }

                    override fun onTaskFailed(errors: Int) {
                        onError("Failed to download $errors tiles")
                    }

                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                        val percent = if (totalTiles > 0) ((progress.toFloat() / totalTiles) * 100).toInt() else 0
                        onProgress(percent.coerceIn(0, 100))
                    }

                    override fun downloadStarted() {}

                    override fun setPossibleTilesInArea(total: Int) {
                        if (total > 0) {
                            totalTiles = total
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("OfflineMapManager", "Error starting tile download", e)
            onError(e.message ?: "Unknown download error")
        }
    }

    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val basePath = File(context.cacheDir, "osmdroid/tiles_v3")
        calculateDirectorySize(basePath)
    }

    suspend fun clearTileCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            val basePath = File(context.cacheDir, "osmdroid/tiles_v3")
            if (basePath.exists()) {
                basePath.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("OfflineMapManager", "Error clearing tile cache", e)
            false
        }
    }

    suspend fun importArchive(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.cacheDir, "osmdroid/tiles_v3")
            if (!destDir.exists()) destDir.mkdirs()

            val destFile = File(destDir, "offline_pack_${System.currentTimeMillis()}.sqlite")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("OfflineMapManager", "Error importing archive", e)
            false
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var bytes = 0L
        dir.listFiles()?.forEach { file ->
            bytes += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return bytes
    }
}
