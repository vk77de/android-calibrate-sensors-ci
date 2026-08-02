// File: ./app/src/main/java/com/example/helloworldkotlinandroid/Quaternion.kt
package com.example.helloworldkotlinandroid

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A unit quaternion (w, x, y, z) representing a 3-D rotation.
 *
 * This is the representation used for the calibration result: the *rotation
 * residual* (aka rotation error / attitude error / calibration offset)
 * between the phone's raw sensor pose and the true pose implied by sighting
 * a celestial body (Moon, Sun, or Venus). A quaternion avoids gimbal lock
 * and the ambiguity of Euler angles, and composes/interpolates cleanly.
 */
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    fun magnitude(): Float = sqrt(w * w + x * x + y * y + z * z)

    /** Returns this quaternion scaled to unit length. Falls back to identity if degenerate. */
    fun normalized(): Quaternion {
        val mag = magnitude()
        if (mag < 1e-8f) return IDENTITY
        return Quaternion(w / mag, x / mag, y / mag, z / mag)
    }

    /** The inverse of a unit quaternion is its conjugate. */
    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    /** Hamilton product: applies [other] first, then this rotation. */
    operator fun times(other: Quaternion): Quaternion = Quaternion(
        w = w * other.w - x * other.x - y * other.y - z * other.z,
        x = w * other.x + x * other.w + y * other.z - z * other.y,
        y = w * other.y - x * other.z + y * other.w + z * other.x,
        z = w * other.z + x * other.y - y * other.x + z * other.w
    )

    /**
     * Rotates the 3-vector [v] by this unit quaternion using the Hamilton
     * sandwich product: v' = q ⊗ v ⊗ q⁻¹ (with q⁻¹ = [conjugate] for a unit
     * quaternion). [v] must have length 3 ([x, y, z]); returns a new length-3
     * [FloatArray].
     *
     * This is what [PlanetariumProjector] uses in place of the old
     * `Matrix.multiplyMV` world/device-space transform.
     */
    fun rotateVector(v: FloatArray): FloatArray {
        val pure = Quaternion(0f, v[0], v[1], v[2])
        val rotated = this * pure * conjugate()
        return floatArrayOf(rotated.x, rotated.y, rotated.z)
    }

    /**
     * Total angular residual represented by this quaternion, in degrees.
     * Useful as a single "how far off is the calibration" telemetry number.
     */
    fun residualAngleDegrees(): Float {
        val clampedW = max(-1f, min(1f, magnitude().let { if (it < 1e-8f) 1f else w / it }))
        return Math.toDegrees((2.0 * acos(clampedW.toDouble()))).toFloat()
    }

    /** Converts to a 4×4 column-major rotation matrix (Android/OpenGL convention). */
    fun toRotationMatrix(): FloatArray {
        val q = normalized()
        val (qw, qx, qy, qz) = q

        val xx = qx * qx
        val yy = qy * qy
        val zz = qz * qz
        val xy = qx * qy
        val xz = qx * qz
        val yz = qy * qz
        val wx = qw * qx
        val wy = qw * qy
        val wz = qw * qz

        val m = FloatArray(16)
        // Column-major: element (row r, col c) lives at m[c * 4 + r]
        m[0] = 1f - 2f * (yy + zz)
        m[1] = 2f * (xy + wz)
        m[2] = 2f * (xz - wy)
        m[3] = 0f
        m[4] = 2f * (xy - wz)
        m[5] = 1f - 2f * (xx + zz)
        m[6] = 2f * (yz + wx)
        m[7] = 0f
        m[8] = 2f * (xz + wy)
        m[9] = 2f * (yz - wx)
        m[10] = 1f - 2f * (xx + yy)
        m[11] = 0f
        m[12] = 0f
        m[13] = 0f
        m[14] = 0f
        m[15] = 1f
        return m
    }

    /**
     * Decomposes into (azimuth/yaw, pitch, roll) degrees, matching the convention
     * used by [android.hardware.SensorManager.getOrientation]. This is provided
     * purely for human-readable telemetry/logging; the quaternion remains the
     * source of truth for the calibration offset itself.
     */
    fun toEulerDegrees(): FloatArray {
        val m = toRotationMatrix()
        // Same convention as SensorManager.getOrientation for a column-major
        // rotation matrix: azimuth = atan2(m[1], m[5]), pitch = asin(-m[9]), roll = atan2(-m[8], m[10])
        val azimuth = atan2(m[1].toDouble(), m[5].toDouble())
        val pitch = asin((-m[9]).toDouble().coerceIn(-1.0, 1.0))
        val roll = atan2((-m[8]).toDouble(), m[10].toDouble())
        return floatArrayOf(
            Math.toDegrees(azimuth).toFloat(),
            Math.toDegrees(pitch).toFloat(),
            Math.toDegrees(roll).toFloat()
        )
    }

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        /** Converts a 4×4 column-major rotation matrix (Android/OpenGL convention) to a quaternion. */
        fun fromRotationMatrix(m: FloatArray): Quaternion {
            val r00 = m[0]
            val r10 = m[1]
            val r20 = m[2]
            val r01 = m[4]
            val r11 = m[5]
            val r21 = m[6]
            val r02 = m[8]
            val r12 = m[9]
            val r22 = m[10]

            val trace = r00 + r11 + r22

            val q = if (trace > 0f) {
                val s = sqrt(trace + 1f) * 2f
                Quaternion(
                    w = 0.25f * s,
                    x = (r21 - r12) / s,
                    y = (r02 - r20) / s,
                    z = (r10 - r01) / s
                )
            } else if (r00 > r11 && r00 > r22) {
                val s = sqrt(1f + r00 - r11 - r22) * 2f
                Quaternion(
                    w = (r21 - r12) / s,
                    x = 0.25f * s,
                    y = (r01 + r10) / s,
                    z = (r02 + r20) / s
                )
            } else if (r11 > r22) {
                val s = sqrt(1f + r11 - r00 - r22) * 2f
                Quaternion(
                    w = (r02 - r20) / s,
                    x = (r01 + r10) / s,
                    y = 0.25f * s,
                    z = (r12 + r21) / s
                )
            } else {
                val s = sqrt(1f + r22 - r00 - r11) * 2f
                Quaternion(
                    w = (r10 - r01) / s,
                    x = (r02 + r20) / s,
                    y = (r12 + r21) / s,
                    z = 0.25f * s
                )
            }
            return q.normalized()
        }

        /**
         * Builds a quaternion from azimuth/pitch/roll degrees using the same rotation
         * convention as the legacy Euler-based calibration offset builder. Kept
         * around only to translate old Euler-based calibration files into quaternions.
         */
        fun fromEulerDegrees(azimuthDeg: Float, pitchDeg: Float, rollDeg: Float): Quaternion {
            val azRad = Math.toRadians(azimuthDeg.toDouble())
            val ptRad = Math.toRadians(pitchDeg.toDouble())
            val rlRad = Math.toRadians(rollDeg.toDouble())

            val sinAz = kotlin.math.sin(azRad).toFloat()
            val cosAz = kotlin.math.cos(azRad).toFloat()
            val sinPt = kotlin.math.sin(ptRad).toFloat()
            val cosPt = kotlin.math.cos(ptRad).toFloat()
            val sinRl = kotlin.math.sin(rlRad).toFloat()
            val cosRl = kotlin.math.cos(rlRad).toFloat()

            val m = FloatArray(16)
            m[0] = cosAz * cosRl - sinAz * sinPt * sinRl
            m[1] = sinAz * cosPt
            m[2] = cosAz * sinRl + sinAz * sinPt * cosRl
            m[4] = -sinAz * cosRl - cosAz * sinPt * sinRl
            m[5] = cosAz * cosPt
            m[6] = -sinAz * sinRl + cosAz * sinPt * cosRl
            m[8] = -sinRl * cosPt
            m[9] = -sinPt
            m[10] = cosRl * cosPt
            m[15] = 1f

            return fromRotationMatrix(m)
        }
    }
}
