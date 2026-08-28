package com.example.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HaversineCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points on the Earth's surface in meters.
     */
    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Formats distance cleanly:
     * Below 1 km -> meters (e.g., "450 m jauhnya")
     * 1 km or above -> kilometers (e.g., "12.400 km jauhnya" or "12.4 km jauhnya")
     */
    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.toInt()} m jauhnya"
        } else {
            val km = distanceMeters / 1000.0
            val formattedKm = String.format("%.1f", km).replace(",", ".")
            "$formattedKm km jauhnya"
        }
    }
}
