// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CelestialCalibrator.kt
package com.example.helloworldkotlinandroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages all calibration-mode mathematics and live sensor fusion.
 *
 * Responsibilities
 * ────────────────
 *  • Continuously converts the raw TYPE_ROTATION_VECTOR sensor stream into
 *    [rawSensorQuaternion] and (once calibrated) into [calibratedQuaternion].
 *  • [setCalibrationOffset]        — restores [calibrationOffsetQuaternion] from a stored [Quaternion].
 *  • [performCelestialCalibration] — computes the rotation *residual* (the calibration
 *                                    offset, aka rotation/attitude error) between the raw
 *                                    sensor pose and the true pose implied by sighting a
 *                                    celestial body (Moon, Sun just after sunset, or Venus)
 *                                    at calibration time, returned as a unit [Quaternion].
 *
 * Quaternion convention
 * ──────────────────────
 * Let q_os be [rawSensorQuaternion] — straight from the Android rotation-vector sensor —
 * and q_c be [calibrationOffsetQuaternion] — the stored rotation-residual / calibration
 * offset. Both map the *device* frame to the *world* frame, same as Android's own
 * rotation matrix convention (`SensorManager.getRotationMatrix` /
 * `getQuaternionFromVector`). The corrected, ready-to-render orientation is composed as:
 *
 *     q_real = q_c ⊗ q_os
 *
 * i.e. apply the raw sensor rotation first, then the calibration correction. This
 * mirrors the composition order the app previously used for the matrix product
 * `calibrationOffsetMatrix × rawSensorMatrix`.
 *
 * Planetarium rendering
 * ─────────────────────
 * Screen-projection mathematics have been intentionally moved to [PlanetariumProjector].
 * Instantiate that class with a reference to this [CelestialCalibrator] so the renderer
 * can read [calibratedQuaternion] without coupling projection logic to calibration logic.
 * Because q_real maps device → world (same as q_os), [PlanetariumProjector] applies its
 * *inverse* (conjugate) to turn a world-space celestial vector into device space — see
 * that class for details, including why this also fixes the previous "apply R instead
 * of R⁻¹" world-to-screen bug.
 */
class CelestialCalibrator : SensorEventListener {

    /** q_os: unit quaternion straight from the sensor (device → world, no calibration applied). */
    var rawSensorQuaternion: Quaternion = Quaternion.IDENTITY
        private set

    /**
     * q_c: unit rotation-residual quaternion that maps the raw sensor frame to the true
     * celestial frame. Populated by [setCalibrationOffset] or [performCelestialCalibration].
     */
    var calibrationOffsetQuaternion: Quaternion = Quaternion.IDENTITY
        private set

    /**
     * q_real = q_c ⊗ q_os: the ready-for-rendering orientation (device → world).
     * When not yet calibrated, this is just a copy of [rawSensorQuaternion].
     */
    var calibratedQuaternion: Quaternion = Quaternion.IDENTITY
        private set

    /** True once a calibration offset has been stored by either calibration method. */
    var isCalibrated = false
        private set

    // ──────────────────────────────────────────────────────────────────────────────
    //  Calibration mathematics
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Restores a previously computed rotation-residual [quaternion] (e.g. loaded from
     * storage) as the active [calibrationOffsetQuaternion] (q_c).
     */
    fun setCalibrationOffset(quaternion: Quaternion) {
        calibrationOffsetQuaternion = quaternion.normalized()
        isCalibrated = true
    }

    /**
     * Performs a celestial calibration sighting and returns the resulting rotation
     * *residual* — the calibration offset, aka rotation error / attitude error — as a
     * unit [Quaternion] (q_c).
     *
     * The same sighting math works regardless of which body was used to aim the device:
     * the Moon, the Sun (typically sighted just after sunset, near the horizon), or the
     * planet Venus. Only [trueAzimuth]/[trueAltitude] — the body's computed true
     * horizontal position at the moment of sighting — differ between bodies; the caller
     * supplies whichever body's position it looked up (see [CelestialObjectsCalculator]
     * and [MoonCalculator]).
     *
     * q_c is derived as:
     *
     *     q_c = q_true ⊗ q_os⁻¹
     *
     * where q_true is the quaternion for the true azimuth/altitude pose and
     * q_os⁻¹ = conjugate(q_os) is the inverse of the current raw sensor reading — the
     * quaternion analogue of the previous `trueRotationMatrix × invertedSensorMatrix`.
     *
     * @param trueAzimuth  True azimuth of the sighted body, in degrees (0° = North).
     * @param trueAltitude True altitude of the sighted body, in degrees above horizon.
     * @return The rotation-residual quaternion (q_c) mapping the raw sensor frame to the
     *         true celestial frame. Also stored as [calibrationOffsetQuaternion].
     */
    fun performCelestialCalibration(trueAzimuth: Float, trueAltitude: Float): Quaternion {
        // q_true = Q_z(-trueAzimuth) ⊗ Q_x(trueAltitude), matching the previous
        // Matrix.rotateM(-trueAzimuth, about Z) then Matrix.rotateM(trueAltitude, about X).
        val halfAzRad = Math.toRadians(-trueAzimuth.toDouble()) / 2.0
        val halfAltRad = Math.toRadians(trueAltitude.toDouble()) / 2.0

        val qz = Quaternion(
            w = cos(halfAzRad).toFloat(),
            x = 0f,
            y = 0f,
            z = sin(halfAzRad).toFloat()
        )
        val qx = Quaternion(
            w = cos(halfAltRad).toFloat(),
            x = sin(halfAltRad).toFloat(),
            y = 0f,
            z = 0f
        )
        val trueQuaternion = (qz * qx).normalized()
        val invertedSensorQuaternion = rawSensorQuaternion.conjugate()

        calibrationOffsetQuaternion = (trueQuaternion * invertedSensorQuaternion).normalized()
        isCalibrated = true

        return calibrationOffsetQuaternion
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  Live sensor fusion
    // ──────────────────────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val q = FloatArray(4)
            // Returns [w, x, y, z], matching the Quaternion(w, x, y, z) constructor order.
            SensorManager.getQuaternionFromVector(q, event.values)
            rawSensorQuaternion = Quaternion(q[0], q[1], q[2], q[3]).normalized()

            calibratedQuaternion = if (isCalibrated) {
                (calibrationOffsetQuaternion * rawSensorQuaternion).normalized()
            } else {
                rawSensorQuaternion
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
