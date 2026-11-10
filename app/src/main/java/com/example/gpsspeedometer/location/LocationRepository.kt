package com.example.gpsspeedometer.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.gpsspeedometer.domain.LocationSample
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager? = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long): Flow<LocationSample> = callbackFlow {
        var isFusedRegistered = false
        var fusedCallback: LocationCallback? = null
        var isLocationManagerRegistered = false
        var systemListener: LocationListener? = null

        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                val isGooglePlayAvailable = try {
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
                } catch (e: Exception) {
                    false
                }

                if (isGooglePlayAvailable) {
                    try {
                        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                            .setMinUpdateIntervalMillis(intervalMs)
                            .build()

                        val callback = object : LocationCallback() {
                            override fun onLocationResult(result: LocationResult) {
                                try {
                                    result.locations.forEach { location ->
                                        if (location != null) {
                                            trySend(location.toLocationSample())
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("LocationRepository", "Error in onLocationResult", e)
                                }
                            }
                        }
                        fusedCallback = callback
                        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                            .addOnFailureListener { e ->
                                Log.e("LocationRepository", "FusedLocationProviderClient async failure", e)
                            }
                        isFusedRegistered = true
                        Log.d("LocationRepository", "Using FusedLocationProviderClient for updates")
                    } catch (e: Exception) {
                        Log.e("LocationRepository", "FusedLocationProviderClient failed, falling back to LocationManager", e)
                        isFusedRegistered = false
                    }
                }

                if (!isFusedRegistered && locationManager != null) {
                    try {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                try {
                                    trySend(location.toLocationSample())
                                } catch (e: Exception) {
                                    Log.e("LocationRepository", "Error in onLocationChanged", e)
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        systemListener = listener

                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                intervalMs,
                                0f,
                                listener,
                                Looper.getMainLooper()
                            )
                            isLocationManagerRegistered = true
                            Log.d("LocationRepository", "Registered GPS_PROVIDER fallback listener")
                        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            locationManager.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                intervalMs,
                                0f,
                                listener,
                                Looper.getMainLooper()
                            )
                            isLocationManagerRegistered = true
                            Log.d("LocationRepository", "Registered NETWORK_PROVIDER fallback listener")
                        }
                    } catch (e: Exception) {
                        Log.e("LocationRepository", "LocationManager fallback failed", e)
                    }
                }
            } else {
                Log.w("LocationRepository", "Cannot request location updates: location permissions missing")
            }
        } catch (e: Exception) {
            Log.e("LocationRepository", "Unexpected error setting up location updates", e)
        }

        awaitClose {
            if (isFusedRegistered && fusedCallback != null) {
                try {
                    client.removeLocationUpdates(fusedCallback)
                } catch (e: Exception) {
                    Log.e("LocationRepository", "Error removing fused updates", e)
                }
            }
            if (isLocationManagerRegistered && systemListener != null && locationManager != null) {
                try {
                    locationManager.removeUpdates(systemListener)
                } catch (e: Exception) {
                    Log.e("LocationRepository", "Error removing LocationManager updates", e)
                }
            }
        }
    }
}

fun Location.toLocationSample(): LocationSample {
    return LocationSample(
        timestamp = this.time,
        lat = this.latitude,
        lng = this.longitude,
        speedMps = if (this.hasSpeed()) this.speed else 0f,
        accuracyMeters = if (this.hasAccuracy()) this.accuracy else 0f,
        bearing = if (this.hasBearing()) this.bearing else 0f
    )
}
