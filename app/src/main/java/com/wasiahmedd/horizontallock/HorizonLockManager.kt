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
 * Reads TYPE_ROTATION_VECTOR (gyro + accel + mag fusion) to compute the device's
 * true camera-plane roll — i.e. how many degrees the phone is rotated about the
 * axis pointing OUT of the lens. This is the value we counter-rotate the preview by.
 *
 * FIX: The old code used SensorManager.getOrientation() which returns Euler angles
 * in the WORLD frame and gives WRONG roll when the phone is held vertically (portrait)
 * for camera use.  The correct approach is to remap the rotation matrix so that
 *   X = right edge of screen  (landscape axis)
 *   Y = top of screen         (portrait axis)
 * and then read orientationAngles[2] from THAT remapped matrix.  After remapping,
 * index [2] gives the true clockwise-tilt of the camera from the horizon.
 */
class HorizonLockManager(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Prefer GAME_ROTATION_VECTOR (no magnetometer — no compass interference).
    // Fall back to ROTATION_VECTOR if unavailable.
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _rollAngle = MutableStateFlow(0f)
    /** Current camera roll in degrees. Positive = tilted clockwise, Negative = anti-clockwise. */
    val rollAngle: StateFlow<Float> = _rollAngle.asStateFlow()

    private val rotationMatrix    = FloatArray(9)
    private val remappedMatrix    = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /** Low-pass filter state */
    private var filteredRoll = 0f

    /**
     * Alpha for the low-pass filter (0–1).
     * 0.80 → fast response (~20 ms lag at 50 Hz), minimal jitter.
     * Raise to 0.90 if you see jitter on a specific device.
     */
    private val alpha = 0.80f

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME   // ~50 Hz — smooth enough at 60fps
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Step 1 — Build 3×3 rotation matrix from quaternion
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Step 2 — Remap so the coordinate system matches the portrait camera view:
        //   new X = device's original X (screen right edge)
        //   new Y = device's original Z (pointing toward user / out of screen)
        // After remapping, orientationAngles[2] is the true camera-plane roll.
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )

        // Step 3 — Decompose remapped matrix into azimuth / pitch / roll
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // orientationAngles[2] = roll in radians → degrees
        // Negate so that tilting RIGHT gives a positive value (clockwise)
        val rawRoll = -Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Step 4 — Low-pass filter
        filteredRoll = alpha * filteredRoll + (1f - alpha) * rawRoll

        _rollAngle.value = filteredRoll
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No-op
    }
}
