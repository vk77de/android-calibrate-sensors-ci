package com.example.helloworldkotlinandroid

import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

class CelestialCalibrator : SensorEventListener {
    val rawSensorMatrix = FloatArray(16)
    val calibrationOffsetMatrix = FloatArray(16)
    val calibratedMatrix = FloatArray(16)
    var isCalibrated = false
        private set

    init {
        Matrix.setIdentityM(calibrationOffsetMatrix, 0)
    }

    fun setCalibrationOffsets(az: Float, pt: Float, rl: Float) {
        val orientationRadians =
            floatArrayOf(
                Math.toRadians(az.toDouble()).toFloat(),
                Math.toRadians(pt.toDouble()).toFloat(),
                Math.toRadians(rl.toDouble()).toFloat()
            )
        SensorManager.getRotationMatrixFromVector(calibrationOffsetMatrix, orientationRadians)
        isCalibrated = true
    }

    fun performCelestialCalibration(trueAzimuth: Float, trueAltitude: Float): FloatArray {
        val trueRotationMatrix = FloatArray(16)
        Matrix.setIdentityM(trueRotationMatrix, 0)
        Matrix.rotateM(trueRotationMatrix, 0, -trueAzimuth, 0f, 1f, 0f)
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

        val orientationRadians = FloatArray(3)
        SensorManager.getOrientation(calibrationOffsetMatrix, orientationRadians)

        return floatArrayOf(
            Math.toDegrees(orientationRadians[0].toDouble()).toFloat(),
            Math.toDegrees(orientationRadians[1].toDouble()).toFloat(),
            Math.toDegrees(orientationRadians[2].toDouble()).toFloat()
        )
    }

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

    fun projectOrientationToScreen(
        azimuth: Double,
        altitude: Double,
        width: Int,
        height: Int
    ): PointF? {
        val azRad = Math.toRadians(azimuth)
        val altRad = Math.toRadians(altitude)

        val worldX = cos(altRad) * sin(azRad)
        val worldY = cos(altRad) * cos(azRad)
        val worldZ = sin(altRad)

        val worldVector = floatArrayOf(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat(), 1.0f)
        val deviceVector = FloatArray(4)

        Matrix.multiplyMV(deviceVector, 0, calibratedMatrix, 0, worldVector, 0)

        if (deviceVector[2] >= 0f) return null

        val centerX = width / 2f
        val centerY = height / 2f
        val cameraFocalFactor = width * 1.1f

        val screenX = centerX + (deviceVector[0] / -deviceVector[2]) * cameraFocalFactor
        val screenY = centerY - (deviceVector[1] / -deviceVector[2]) * cameraFocalFactor

        return PointF(screenX, screenY)
    }
}
