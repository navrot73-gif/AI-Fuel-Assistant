package com.navrot.aifuelassistant.location

import android.location.Location

/**
 * GPS coordinate smoother using two 1D Kalman filters (lat + lon).
 *
 * Features:
 * - Smooths raw GPS measurements to reduce jitter
 * - Detects jumps >500m and resets filters automatically
 * - Singleton object, safe to call from any thread
 */
object LocationSmoother {
    private val latFilter = KalmanFilter()
    private val lonFilter = KalmanFilter()
    private var lastSmoothedLat: Double? = null
    private var lastSmoothedLon: Double? = null

    /**
     * Smooth a new raw GPS coordinate.
     *
     * Automatically resets both filters if the new point is
     * more than 500 meters away from the last smoothed point.
     *
     * @param lat Raw latitude from GPS
     * @param lon Raw longitude from GPS
     * @return Smoothed (latitude, longitude) pair
     */
    @Synchronized
    fun smooth(lat: Double, lon: Double): Pair<Double, Double> {
        // Jump detection: if >500m from last smoothed point, reset filters
        if (lastSmoothedLat != null && lastSmoothedLon != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastSmoothedLat!!, lastSmoothedLon!!,
                lat, lon,
                results
            )
            if (results[0] > JUMP_THRESHOLD_METERS) {
                latFilter.reset()
                lonFilter.reset()
            }
        }

        val smoothedLat = latFilter.update(lat)
        val smoothedLon = lonFilter.update(lon)

        lastSmoothedLat = smoothedLat
        lastSmoothedLon = smoothedLon

        return Pair(smoothedLat, smoothedLon)
    }

    /**
     * Reset all filters and tracking state.
     */
    @Synchronized
    fun reset() {
        latFilter.reset()
        lonFilter.reset()
        lastSmoothedLat = null
        lastSmoothedLon = null
    }

    private const val JUMP_THRESHOLD_METERS = 500
}