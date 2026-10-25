package com.example.gpsspeedometer.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gpsspeedometer.speedlimit.RoadSpeedSegment
import com.example.gpsspeedometer.ui.components.SpeedLimitSign
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onBack: UnitCallback
) {
    val context = LocalContext.current
    val tripState by viewModel.tripState.collectAsState()
    val speedLimit by viewModel.speedLimitInfo.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pastTripPoints by viewModel.pastTripPoints.collectAsState()
    val pastTrip by viewModel.pastTrip.collectAsState()
    val showRoadSpeedLimits by viewModel.showRoadSpeedLimits.collectAsState()
    val roadSegments by viewModel.roadSegments.collectAsState()

    var showLayerMenu by remember { mutableStateOf(false) }

    val isPastTripView = pastTrip != null || pastTripPoints.isNotEmpty()
    val title = pastTrip?.name ?: if (isPastTripView) "Trip Route" else "Live Road Map"

    val roadSpeedOverlay = remember { FolderOverlay() }
    val userMarkerRef = remember { mutableStateOf<Marker?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(AppTileSources.OpenStreetMap)
            setMultiTouchControls(true)
            controller.setZoom(16.5)

            // Legal OpenStreetMap attribution overlay
            val copyrightOverlay = CopyrightOverlay(context).apply {
                setAlignRight(true)
                setTextSize(10)
            }
            overlays.add(copyrightOverlay)
            overlays.add(roadSpeedOverlay)
        }
    }

    // Apply offline mode to map tile downloader (blocks 100% of outgoing network requests when true)
    LaunchedEffect(settings.offlineMode) {
        mapView.setUseDataConnection(!settings.offlineMode)
    }

    DisposableEffect(mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val bbox = mapView.boundingBox
                viewModel.onViewportChanged(
                    bbox.latSouth,
                    bbox.lonWest,
                    bbox.latNorth,
                    bbox.lonEast,
                    mapView.zoomLevelDouble
                )
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val bbox = mapView.boundingBox
                viewModel.onViewportChanged(
                    bbox.latSouth,
                    bbox.lonWest,
                    bbox.latNorth,
                    bbox.lonEast,
                    mapView.zoomLevelDouble
                )
                return false
            }
        }
        mapView.addMapListener(mapListener)
        mapView.onResume()

        onDispose {
            mapView.removeMapListener(mapListener)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    // Render road speed limits
    LaunchedEffect(roadSegments, showRoadSpeedLimits, settings.useMph) {
        roadSpeedOverlay.items.clear()
        if (showRoadSpeedLimits && roadSegments.isNotEmpty()) {
            for (segment in roadSegments) {
                val geoPoints = segment.coordinates.map { GeoPoint(it.first, it.second) }
                val speedMph = segment.speedLimitMph

                // Color-code road line by speed range
                val roadColor = when {
                    speedMph <= 25 -> 0xFF2E7D32.toInt() // Emerald green (neighborhood/slow)
                    speedMph <= 45 -> 0xFF1976D2.toInt() // Blue (arterials)
                    speedMph <= 65 -> 0xFFF57C00.toInt() // Orange (highways)
                    else -> 0xFFD32F2F.toInt()           // Red (fast expressway/interstate)
                }

                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = roadColor
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.alpha = 180
                }
                roadSpeedOverlay.add(polyline)

                // Place a speed shield at the midpoint of each segment
                if (geoPoints.size >= 2) {
                    val midPoint = geoPoints[geoPoints.size / 2]
                    val speedDisplay = if (settings.useMph) "$speedMph" else "${segment.speedLimitKmh}"
                    val badgeIcon = createSpeedBadgeDrawable(context, speedDisplay, settings.useMph)

                    val badgeMarker = Marker(mapView).apply {
                        position = midPoint
                        icon = badgeIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setTitle("${segment.name ?: "Road"}: $speedDisplay ${if (settings.useMph) "MPH" else "KM/H"}")
                    }
                    roadSpeedOverlay.add(badgeMarker)
                }
            }
        }
        mapView.invalidate()
    }

    // Handle past trip points polyline and camera fitting
    LaunchedEffect(pastTripPoints) {
        if (pastTripPoints.isNotEmpty()) {
            mapView.overlays.removeAll { it is Polyline && it != roadSpeedOverlay }

            val geoPoints = pastTripPoints.map { GeoPoint(it.lat, it.lng) }
            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = primaryColorArgb
                outlinePaint.strokeWidth = 10f
            }
            mapView.overlays.add(0, polyline)

            // Start marker
            val startPoint = geoPoints.first()
            val startMarker = Marker(mapView).apply {
                position = startPoint
                setTitle("Start")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(startMarker)

            // End marker
            val endPoint = geoPoints.last()
            val endMarker = Marker(mapView).apply {
                position = endPoint
                setTitle("End")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(endMarker)

            // Fit bounds with padding
            val minLat = pastTripPoints.minOf { it.lat }
            val maxLat = pastTripPoints.maxOf { it.lat }
            val minLng = pastTripPoints.minOf { it.lng }
            val maxLng = pastTripPoints.maxOf { it.lng }

            val boundingBox = BoundingBox(
                maxLat + 0.002,
                maxLng + 0.002,
                minLat - 0.002,
                minLng - 0.002
            )
            mapView.zoomToBoundingBox(boundingBox, true, 80)
            mapView.invalidate()
        }
    }

    val livePoints = remember { mutableListOf<GeoPoint>() }
    val livePolyline = remember {
        Polyline().apply {
            outlinePaint.color = primaryColorArgb
            outlinePaint.strokeWidth = 10f
        }
    }

    // Live position updates
    LaunchedEffect(tripState.currentLat, tripState.currentLng) {
        if (!isPastTripView && tripState.currentLat != 0.0 && tripState.currentLng != 0.0) {
            val userGeoPoint = GeoPoint(tripState.currentLat, tripState.currentLng)

            // If recording, maintain live route polyline
            if (tripState.isRecording && !tripState.isPaused) {
                if (!mapView.overlays.contains(livePolyline)) {
                    mapView.overlays.add(0, livePolyline)
                }
                livePoints.add(userGeoPoint)
                livePolyline.setPoints(livePoints)
            } else if (!tripState.isRecording && livePoints.isNotEmpty()) {
                livePoints.clear()
                livePolyline.setPoints(emptyList())
                mapView.overlays.remove(livePolyline)
            }

            // Update user marker
            userMarkerRef.value?.let { mapView.overlays.remove(it) }
            val userMarker = Marker(mapView).apply {
                position = userGeoPoint
                setTitle("Current Location")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                if (tripState.bearing != 0f) {
                    rotation = tripState.bearing
                }
            }
            userMarkerRef.value = userMarker
            mapView.overlays.add(userMarker)

            mapView.controller.animateTo(userGeoPoint)
            mapView.invalidate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (settings.offlineMode) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "Offline (0 Net)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showLayerMenu = true }) {
                            Icon(Icons.Default.Layers, contentDescription = "Map Layers")
                        }
                        DropdownMenu(
                            expanded = showLayerMenu,
                            onDismissRequest = { showLayerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Road Speed Limits: ${if (showRoadSpeedLimits) "ON" else "OFF"}") },
                                trailingIcon = if (showRoadSpeedLimits) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    viewModel.toggleRoadSpeedLimits(!showRoadSpeedLimits)
                                    showLayerMenu = false
                                    val status = if (!showRoadSpeedLimits) "enabled" else "disabled"
                                    Toast.makeText(context, "Road speed limits $status", Toast.LENGTH_SHORT).show()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Offline Mode: ${if (settings.offlineMode) "ON (0 Net)" else "OFF"}") },
                                trailingIcon = if (settings.offlineMode) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    val newMode = !settings.offlineMode
                                    viewModel.toggleOfflineMode(newMode)
                                    showLayerMenu = false
                                    val msg = if (newMode) "Offline Mode: 0 network bytes" else "Online Mode: network enabled"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Source: OpenStreetMap (No Key)") },
                                enabled = false,
                                onClick = { }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isPastTripView && tripState.currentLat != 0.0) {
                FloatingActionButton(
                    onClick = {
                        val currentGeo = GeoPoint(tripState.currentLat, tripState.currentLng)
                        mapView.controller.animateTo(currentGeo)
                        mapView.controller.setZoom(17.0)
                    }
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Speed limit badge overlay (top right)
            val currentSpeedToCompare = if (settings.useMph) tripState.currentSpeedMph else tripState.currentSpeedKmh
            val speedLimitToCompare = if (settings.useMph) speedLimit.speedLimitMph else speedLimit.speedLimitKmh
            val isExceeded = speedLimitToCompare != null && currentSpeedToCompare > speedLimitToCompare

            SpeedLimitSign(
                speedLimitMph = speedLimit.speedLimitMph,
                speedLimitKmh = speedLimit.speedLimitKmh,
                useMph = settings.useMph,
                isExceeded = isExceeded,
                roadName = speedLimit.roadName,
                isLoading = speedLimit.isLoading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

private typealias UnitCallback = () -> Unit

/**
 * Creates a speed badge drawable to display posted road speed limits on the map.
 * US style: crisp rounded rectangle with black border.
 * EU style: circle with red border.
 */
fun createSpeedBadgeDrawable(context: Context, speedText: String, useMph: Boolean): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (32 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (useMph) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 14f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    if (useMph) {
        val rect = RectF(2f * density, 2f * density, width - 2f * density, height - 2f * density)
        canvas.drawRoundRect(rect, 5f * density, 5f * density, bgPaint)
        canvas.drawRoundRect(rect, 5f * density, 5f * density, borderPaint)
    } else {
        val radius = (width / 2f) - (2f * density)
        canvas.drawCircle(width / 2f, height / 2f, radius, bgPaint)
        canvas.drawCircle(width / 2f, height / 2f, radius, borderPaint)
    }

    val yPos = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(speedText, width / 2f, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
