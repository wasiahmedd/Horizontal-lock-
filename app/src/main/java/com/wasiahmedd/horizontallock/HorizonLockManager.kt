package com.wasiahmedd.horizontallock

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HorizonLockManager
 *
 * Reads the TYPE_ROTATION_VECTOR sensor (gyroscope + accelerometer fusion) to compute
 * the device's current roll angle. This roll value is used by CameraHelper to counter-rotate
 * the camera preview, keeping the horizon level at all times.
 *
 * Low-pass filtering is applied to smooth sensor noise without introducing noticeable lag.
 */
class HorizonLockManager(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_ROTATION_VECTOR is a virtual sensor that fuses gyroscope + accelerometer + magnetometer.
    // It provides the most stable and drift-free orientation estimate available on Android.
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _rollAngle = MutableStateFlow(0f)
    /** Current device roll in degrees. Positive = tilted right, Negative = tilted left. */
    val rollAngle: StateFlow<Float> = _rollAngle.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /** Filtered roll for smooth animation. */
    private var filteredRoll = 0f

    /**
     * Low-pass filter coefficient (0–1).
     * Higher value → smoother but more latency.
     * 0.85 gives sub-pixel jitter suppression with ~32ms latency at 60fps.
     */
    private val alpha = 0.85f

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME   // ~50Hz — fast enough for smooth preview
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Build rotation matrix from quaternion data
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Decompose into azimuth (Z), pitch (X), roll (Y)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[2] is the roll in radians — convert to degrees
        val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Low-pass filter smoothing
        filteredRoll = alpha * filteredRoll + (1f - alpha) * rawRoll

        _rollAngle.value = filteredRoll
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No-op: rotation vector sensor accuracy changes don't affect our usage
    }
}
