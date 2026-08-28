package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LocationHelper {

    private const val TAG = "LocationHelper"

    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val country: String
    )

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationResult? {
        if (!hasLocationPermission(context)) {
            return LocationResult(latitude = -6.2088, longitude = 106.8456, country = "Indonesia")
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await() ?: fusedClient.lastLocation.await() ?: run {
                return LocationResult(latitude = -6.2088, longitude = 106.8456, country = "Indonesia")
            }

            val country = getCountryFromCoordinates(context, location.latitude, location.longitude)
            LocationResult(
                latitude = location.latitude,
                longitude = location.longitude,
                country = country
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device location: ${e.message}")
            LocationResult(latitude = -6.2088, longitude = 106.8456, country = "Indonesia")
        }
    }

    suspend fun getCountryFromCoordinates(context: Context, lat: Double, lon: Double): String {
        return try {
            if (!Geocoder.isPresent()) {
                return "Indonesia"
            }
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val country = addresses.firstOrNull()?.countryName ?: "Indonesia"
                            continuation.resume(country)
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume("Indonesia")
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                addresses?.firstOrNull()?.countryName ?: "Indonesia"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder exception: ${e.message}")
            "Indonesia"
        }
    }
}
