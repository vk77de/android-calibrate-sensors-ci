// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CelestialCalibrator.kt
package com.example.helloworldkotlinandroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages all calibration-mode mathematics and live sensor fusion.
 *
 * Responsibilities
 * ────────────────
 *  • Continuously converts the raw TYPE_ROTATION_VECTOR sensor stream into
 *    [rawSensorMatrix] and (once calibrated) into [calibratedMatrix].
 *  • [setCalibrationOffsets]       — builds the offset matrix from stored az/pitch/roll scalars.
 *  • [performCelestialCalibration] — computes the offset by aligning the sensor pose to a
 *                                    known celestial body position at calibration time.
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
     * Populated by [setCalibrationOffsets] or [performCelestialCalibration].
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

    fun setCalibrationOffsets(az: Float, pt: Float, rl: Float) {
        val azRad = Math.toRadians(az.toDouble())
        val ptRad = Math.toRadians(pt.toDouble())
        val rlRad = Math.toRadians(rl.toDouble())

        val sinAz = sin(azRad).toFloat(); val cosAz = cos(azRad).toFloat()
        val sinPt = sin(ptRad).toFloat(); val cosPt = cos(ptRad).toFloat()
        val sinRl = sin(rlRad).toFloat(); val cosRl = cos(rlRad).toFloat()

        Matrix.setIdentityM(calibrationOffsetMatrix, 0)

        calibrationOffsetMatrix[0] =  cosAz * cosRl - sinAz * sinPt * sinRl
        calibrationOffsetMatrix[1] =  sinAz * cosPt
        calibrationOffsetMatrix[2] =  cosAz * sinRl + sinAz * sinPt * cosRl
        calibrationOffsetMatrix[4] = -sinAz * cosRl - cosAz * sinPt * sinRl
        calibrationOffsetMatrix[5] =  cosAz * cosPt
        calibrationOffsetMatrix[6] = -sinAz * sinRl + cosAz * sinPt * cosRl
        calibrationOffsetMatrix[8]  = -sinRl * cosPt
        calibrationOffsetMatrix[9]  = -sinPt
        calibrationOffsetMatrix[10] =  cosRl * cosPt

        isCalibrated = true
    }

    fun performCelestialCalibration(trueAzimuth: Float, trueAltitude: Float): FloatArray {
        val trueRotationMatrix = FloatArray(16)
        Matrix.setIdentityM(trueRotationMatrix, 0)
        Matrix.rotateM(trueRotationMatrix, 0, -trueAzimuth, 0f, 0f, 1f)
        Matrix.rotateM(trueRotationMatrix, 0,  trueAltitude, 1f, 0f, 0f)

        val invertedSensorMatrix = FloatArray(16)
        Matrix.invertM(invertedSensorMatrix, 0, rawSensorMatrix, 0)

        Matrix.multiplyMM(calibrationOffsetMatrix, 0, trueRotationMatrix, 0, invertedSensorMatrix, 0)
        isCalibrated = true

        val orientationRadians = FloatArray(3)
        SensorManager.getOrientation(calibrationOffsetMatrix, orientationRadians)
        return floatArrayOf(
            Math.toDegrees(orientationRadians[0].toDouble()).toFloat(),
            Math.toDegrees(orientationRadians[1].toDouble()).toFloat(),
            Math.toDegrees(orientationRadians[2].toDouble()).toFloat()
        )
    }

    // ──────────────────────────────────────────────────────────────────────────────
    //  Live sensor fusion
    // ──────────────────────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rawSensorMatrix, event.values)
            if (isCalibrated) {
                Matrix.multiplyMM(calibratedMatrix, 0, calibrationOffsetMatrix, 0, rawSensorMatrix, 0)
            } else {
                System.arraycopy(rawSensorMatrix, 0, calibratedMatrix, 0, 16)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}