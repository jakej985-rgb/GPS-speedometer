package com.example.gpsspeedometer.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gpsspeedometer.ui.components.SpeedLimitSign
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.widget.Toast
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tripState by viewModel.tripState.collectAsState()
    val speedLimit by viewModel.speedLimitInfo.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pastTripPoints by viewModel.pastTripPoints.collectAsState()
    val pastTrip by viewModel.pastTrip.collectAsState()

    var useCartoLayer by remember { mutableStateOf(false) }

    val isPastTripView = pastTrip != null || pastTripPoints.isNotEmpty()
    val title = pastTrip?.name ?: if (isPastTripView) "Trip Route" else "Live Road Map"

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
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    // Handle past trip points polyline and camera fitting
    LaunchedEffect(pastTripPoints) {
        if (pastTripPoints.isNotEmpty()) {
            mapView.overlays.clear()

            val geoPoints = pastTripPoints.map { GeoPoint(it.lat, it.lng) }
            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = primaryColorArgb
                outlinePaint.strokeWidth = 10f
            }
            mapView.overlays.add(polyline)

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

    // Live position updates
    LaunchedEffect(tripState.currentLat, tripState.currentLng) {
        if (!isPastTripView && tripState.currentLat != 0.0 && tripState.currentLng != 0.0) {
            val userGeoPoint = GeoPoint(tripState.currentLat, tripState.currentLng)

            // Remove previous user marker and add updated marker
            val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
            mapView.overlays.removeAll(existingMarkers)

            val userMarker = Marker(mapView).apply {
                position = userGeoPoint
                setTitle("Current Location")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
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
                    IconButton(
                        onClick = {
                            useCartoLayer = !useCartoLayer
                            val newSource = if (useCartoLayer) AppTileSources.CartoVoyager else AppTileSources.OpenStreetMap
                            mapView.setTileSource(newSource)
                            mapView.invalidate()
                            val name = if (useCartoLayer) "Carto Voyager" else "OpenStreetMap"
                            Toast.makeText(context, "Map layer: $name", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Toggle Map Layer")
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
