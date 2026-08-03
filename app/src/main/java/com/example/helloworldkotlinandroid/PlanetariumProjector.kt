// File: ./app/src/main/java/com/example/helloworldkotlinandroid/PlanetariumProjector.kt
package com.example.helloworldkotlinandroid

import android.graphics.PointF

/**
 * Handles all mathematical projection operations exclusively for the planetarium renderer.
 *
 * Converts a celestial body's world-frame ENU (East, North, Up) unit vector into a 2-D
 * screen pixel position using the live calibrated orientation quaternion (q_real)
 * maintained by [CelestialCalibrator]. No azimuth/altitude intermediate is ever formed,
 * consistent with [MoonCalculator] and [CelestialObjectsCalculator].
 *
 * Coordinate conventions
 * ──────────────────────
 *  • World ENU : East/North/Up unit-vector components, right-handed, Y = North, Z = up
 *  • Screen    : origin at top-left; +X right, +Y down
 *
 * Projection pipeline
 * ───────────────────
 *  1. World-space ENU vector rotated into device space by q_real⁻¹  →  device-space vector
 *  2. Objects behind the camera (deviceVector.z ≥ 0) are culled (return null)
 *  3. Perspective divide with a fixed focal factor  →  screen pixel coordinates
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
     * Projects a celestial object given as a world-frame ENU unit vector onto the screen.
     *
     * @param east, north, up  World-frame East/North/Up unit-vector components of the
     *                          body's direction (see [MoonCalculator.EnuVector]).
     * @param width    Viewport width in pixels.
     * @param height   Viewport height in pixels.
     * @return Screen position as [PointF], or **null** if the object is behind the viewer.
     */
    fun projectToScreen(east: Double, north: Double, up: Double, width: Int, height: Int): PointF? {
        val worldVector = floatArrayOf(east.toFloat(), north.toFloat(), up.toFloat())

        // ── Step 1: World → device space via q_real⁻¹ (conjugate of q_real) ──
        val deviceVector = calibrator.calibratedQuaternion.conjugate().rotateVector(worldVector)

        // ── Step 2: Cull objects behind the camera ──
        //    In device space, the camera looks along −Z, so objects with Z ≥ 0 are behind.
        if (deviceVector[2] >= 0f) return null

        // ── Step 3: Perspective divide → screen pixel coordinates ──
        //    cameraFocalFactor approximates a ~42 ° half-FOV for a typical phone held upright.
        val centerX = width / 2f
        val centerY = height / 2f
        val cameraFocalFactor = width * 1.1f

        val screenX = centerX + (deviceVector[0] / -deviceVector[2]) * cameraFocalFactor
        val screenY = centerY - (deviceVector[1] / -deviceVector[2]) * cameraFocalFactor

        return PointF(screenX, screenY)
    }
}
