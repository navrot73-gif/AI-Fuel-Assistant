package com.navrot.aifuelassistant.location

/**
 * 1D Kalman filter for smoothing noisy GPS coordinates.
 *
 * Used separately for latitude and longitude.
 *
 * @param processNoise How much we trust the model (default 0.00001)
 * @param measurementNoise How much we trust GPS sensor (default 0.0001)
 * @param estimatedError Initial estimation error (default 1.0)
 */
class KalmanFilter(
    private val processNoise: Double = 0.00001,
    private val measurementNoise: Double = 0.0001,
    private var estimatedError: Double = 1.0
) {
    private var state: Double = 0.0
    private var initialized = false

    /**
     * Feed a new raw measurement and get the smoothed value.
     *
     * @param measurement Raw GPS coordinate (lat or lon)
     * @return Smoothed coordinate after Kalman gain correction
     */
    @Synchronized
    fun update(measurement: Double): Double {
        if (!initialized) {
            state = measurement
            initialized = true
            return state
        }

        // Kalman gain
        val kalmanGain = estimatedError / (estimatedError + measurementNoise)

        // Update state estimate
        state += kalmanGain * (measurement - state)

        // Update error estimate
        estimatedError = (1.0 - kalmanGain) * estimatedError + processNoise

        return state
    }

    /**
     * Reset internal state. Call when GPS jumps >500m.
     */
    @Synchronized
    fun reset() {
        initialized = false
        estimatedError = 1.0
    }
}