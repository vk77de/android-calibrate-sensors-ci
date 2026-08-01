// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CelestialCalibrator.kt
package com.example.helloworldkotlinandroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix

/**
 * Manages all calibration-mode mathematics and live sensor fusion.
 *
 * Responsibilities
 * ────────────────
 *  • Continuously converts the raw TYPE_ROTATION_VECTOR sensor stream into
 *    [rawSensorMatrix] and (once calibrated) into [calibratedMatrix].
 *  • [setCalibrationOffset]        — rebuilds the offset matrix from a stored [Quaternion].
 *  • [performCelestialCalibration] — computes the rotation *residual* (the calibration
 *                                    offset, aka rotation/attitude error) between the raw
 *                                    sensor pose and the true pose implied by sighting a
 *                                    celestial body (Moon, Sun just after sunset, or Venus)
 *                                    at calibration time, returned as a unit [Quaternion].
 *
 * Planetarium rendering
 * ─────────────────────
 * Screen-projection mathematics have been intentionally moved to [PlanetariumProjector].
 * Instantiate that class with a reference to this [CelestialCalibrator] so the renderer
 * can read [calibratedMatrix] without coupling projection logic to calibration logic.
 */
class CelestialCalibrator : SensorEventListener {

    /** Raw 4×4 column-major rotation matrix straight from the sensor (no calibration applied). */
    val rawSensorMatrix = FloatArray(16)

    /**
     * 4×4 column-major offset matrix that maps the raw sensor frame to the true celestial frame.
     * Populated by [setCalibrationOffset] or [performCelestialCalibration].
     */
    val calibrationOffsetMatrix = FloatArray(16)

    /**
     * 4×4 column-major matrix that is ready for rendering: [calibrationOffsetMatrix] × [rawSensorMatrix].
     * When not yet calibrated, this is a copy of [rawSensorMatrix].
     */
    val calibratedMatrix = FloatArray(16)

    /** True once a calibration offset has been stored by either calibration method. */
    var isCalibrated = false
        private set

    init {
        Matrix.setIdentityM(calibrationOffsetMatrix, 0)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  Calibration mathematics
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Restores a previously computed rotation-residual [quaternion] (e.g. loaded from
     * storage) as the active [calibrationOffsetMatrix].
     */
    fun setCalibrationOffset(quaternion: Quaternion) {
        val matrix = quaternion.normalized().toRotationMatrix()
        System.arraycopy(matrix, 0, calibrationOffsetMatrix, 0, 16)
        isCalibrated = true
    }

    /**
     * Performs a celestial calibration sighting and returns the resulting rotation
     * *residual* — the calibration offset, aka rotation error / attitude error — as a
     * unit [Quaternion].
     *
     * The same sighting math works regardless of which body was used to aim the device:
     * the Moon, the Sun (typically sighted just after sunset, near the horizon), or the
     * planet Venus. Only [trueAzimuth]/[trueAltitude] — the body's computed true
     * horizontal position at the moment of sighting — differ between bodies; the caller
     * supplies whichever body's position it looked up (see [CelestialObjectsCalculator]
     * and [MoonCalculator]).
     *
     * @param trueAzimuth  True azimuth of the sighted body, in degrees (0° = North).
     * @param trueAltitude True altitude of the sighted body, in degrees above horizon.
     * @return The rotation-residual quaternion mapping the raw sensor frame to the true
     *         celestial frame. Also stored as a matrix in [calibrationOffsetMatrix].
     */
    fun performCelestialCalibration(trueAzimuth: Float, trueAltitude: Float): Quaternion {
        val trueRotationMatrix = FloatArray(16)
        Matrix.setIdentityM(trueRotationMatrix, 0)
        Matrix.rotateM(trueRotationMatrix, 0, -trueAzimuth, 0f, 0f, 1f)
        Matrix.rotateM(trueRotationMatrix, 0, trueAltitude, 1f, 0f, 0f)

        val invertedSensorMatrix = FloatArray(16)
        Matrix.invertM(invertedSensorMatrix, 0, rawSensorMatrix, 0)

        Matrix.multiplyMM(
            calibrationOffsetMatrix,
            0,
            trueRotationMatrix,
            0,
            invertedSensorMatrix,
            0
        )
        isCalibrated = true

        return Quaternion.fromRotationMatrix(calibrationOffsetMatrix)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  Live sensor fusion
    // ──────────────────────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rawSensorMatrix, event.values)
            if (isCalibrated) {
                Matrix.multiplyMM(
                    calibratedMatrix,
                    0,
                    calibrationOffsetMatrix,
                    0,
                    rawSensorMatrix,
                    0
                )
            } else {
                System.arraycopy(rawSensorMatrix, 0, calibratedMatrix, 0, 16)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
