// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CelestialCalibrator.kt
package com.example.helloworldkotlinandroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sign
import kotlin.math.sqrt

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
     * planet Venus. Only [trueDirection] — the body's computed true position in the world
     * frame at the moment of sighting, as a unit East/North/Up vector — differs between
     * bodies; the caller supplies whichever body's position it looked up (see
     * [CelestialObjectsCalculator] and [MoonCalculator]), with no azimuth/altitude
     * involved at any point.
     *
     * q_c is derived as:
     *
     *     q_c = q_true ⊗ q_os⁻¹
     *
     * where q_true is the quaternion that carries the device's reference forward axis
     * (0,1,0) onto [trueDirection], and q_os⁻¹ = conjugate(q_os) is the inverse of the
     * current raw sensor reading — the quaternion analogue of the previous
     * `trueRotationMatrix × invertedSensorMatrix`.
     *
     * @param trueDirection True position of the sighted body, as a unit ENU
     *                       (East, North, Up) vector in the world frame.
     * @return The rotation-residual quaternion (q_c) mapping the raw sensor frame to the
     *         true celestial frame. Also stored as [calibrationOffsetQuaternion].
     */
    fun performCelestialCalibration(trueDirection: MoonCalculator.EnuVector): Quaternion {
        // q_true = Q_z(-Az) ⊗ Q_x(Alt), same rotation as before, but Az/Alt are never formed
        // as angles: their half-angle sin/cos are recovered algebraically straight from the
        // ENU components (Up = sin(Alt); East,North = cos(Alt)*sin(Az), cos(Alt)*cos(Az)).
        val e = trueDirection.east
        val n = trueDirection.north
        val u = trueDirection.up

        val cosAlt = sqrt(e * e + n * n)
        val sinAlt = u

        val cosAz: Double
        val sinAz: Double
        if (cosAlt < 1e-9) {
            // Body is at zenith/nadir: azimuth is undefined. Matches the previous
            // atan2(0, 0) = 0 convention.
            cosAz = 1.0
            sinAz = 0.0
        } else {
            cosAz = n / cosAlt
            sinAz = e / cosAlt
        }

        val cosHalfAlt = sqrt((1.0 + cosAlt) / 2.0)
        val sinHalfAlt = sqrt((1.0 - cosAlt) / 2.0) * sign(sinAlt)
        val cosHalfAz = sqrt((1.0 + cosAz) / 2.0)
        val sinHalfAz = sqrt((1.0 - cosAz) / 2.0) * sign(sinAz)

        val qz = Quaternion(
            w = cosHalfAz.toFloat(),
            x = 0f,
            y = 0f,
            z = -sinHalfAz.toFloat()
        )
        val qx = Quaternion(
            w = cosHalfAlt.toFloat(),
            x = sinHalfAlt.toFloat(),
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
