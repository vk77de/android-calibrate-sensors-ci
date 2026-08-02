// File: ./app/src/main/java/com/example/helloworldkotlinandroid/PlanetariumProjector.kt
package com.example.helloworldkotlinandroid

import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles all mathematical projection operations exclusively for the planetarium renderer.
 *
 * Converts celestial coordinates (azimuth + altitude) into 2-D screen pixel positions
 * using the live calibrated orientation quaternion (q_real) maintained by
 * [CelestialCalibrator].
 *
 * Coordinate conventions
 * ──────────────────────
 *  • Azimuth  : degrees, 0 ° = North, increases clockwise (East = 90 °)
 *  • Altitude : degrees above the horizon  (zenith = 90 °, nadir = −90 °)
 *  • Screen   : origin at top-left; +X right, +Y down
 *
 * Projection pipeline
 * ───────────────────
 *  1. Spherical → Cartesian world-space vector
 *  2. World vector rotated into device space by q_real⁻¹  →  device-space vector
 *  3. Objects behind the camera (deviceVector.z ≥ 0) are culled (return null)
 *  4. Perspective divide with a fixed focal factor  →  screen pixel coordinates
 *
 * Why the *inverse* of q_real
 * ────────────────────────────
 * [CelestialCalibrator.calibratedQuaternion] (q_real = q_c ⊗ q_os) uses the same
 * device → world convention as the underlying Android rotation sensor. To take a
 * world-space celestial direction and express it in device/camera space — which is
 * what step 2 needs — the *inverse* rotation must be applied. For a unit quaternion
 * the inverse is simply its conjugate, so step 2 uses
 * `calibrator.calibratedQuaternion.conjugate().rotateVector(worldVector)`.
 *
 * This replaces the previous `Matrix.multiplyMV(..., calibratedMatrix, ..., worldVector, ...)`
 * call, which applied the calibrated matrix directly (i.e. the forward, device→world
 * transform) to a world-space vector — the confirmed source of the 180°/altitude-flip
 * bug in planetarium mode.
 */
class PlanetariumProjector(private val calibrator: CelestialCalibrator) {

    /**
     * Projects a celestial object at ([azimuth], [altitude]) onto the screen.
     *
     * @param azimuth  Horizontal angle in degrees (0 ° = North, clockwise).
     * @param altitude Vertical angle in degrees above the horizon.
     * @param width    Viewport width in pixels.
     * @param height   Viewport height in pixels.
     * @return Screen position as [PointF], or **null** if the object is behind the viewer.
     */
    fun projectToScreen(azimuth: Double, altitude: Double, width: Int, height: Int): PointF? {
        val azRad = Math.toRadians(azimuth)
        val altRad = Math.toRadians(altitude)

        // ── Step 1: Spherical → Cartesian (right-handed, Y = North, Z = up) ──
        val worldX = cos(altRad) * sin(azRad)
        val worldY = cos(altRad) * cos(azRad)
        val worldZ = sin(altRad)

        val worldVector = floatArrayOf(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat())

        // ── Step 2: World → device space via q_real⁻¹ (conjugate of q_real) ──
        val deviceVector = calibrator.calibratedQuaternion.conjugate().rotateVector(worldVector)

        // ── Step 3: Cull objects behind the camera ──
        //    In device space, the camera looks along −Z, so objects with Z ≥ 0 are behind.
        if (deviceVector[2] >= 0f) return null

        // ── Step 4: Perspective divide → screen pixel coordinates ──
        //    cameraFocalFactor approximates a ~42 ° half-FOV for a typical phone held upright.
        val centerX = width / 2f
        val centerY = height / 2f
        val cameraFocalFactor = width * 1.1f

        val screenX = centerX + (deviceVector[0] / -deviceVector[2]) * cameraFocalFactor
        val screenY = centerY - (deviceVector[1] / -deviceVector[2]) * cameraFocalFactor

        return PointF(screenX, screenY)
    }
}
