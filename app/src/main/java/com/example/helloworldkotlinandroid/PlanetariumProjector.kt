// File: ./app/src/main/java/com/example/helloworldkotlinandroid/PlanetariumProjector.kt
package com.example.helloworldkotlinandroid

import android.graphics.PointF
import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles all mathematical projection operations exclusively for the planetarium renderer.
 *
 * Converts celestial coordinates (azimuth + altitude) into 2-D screen pixel positions
 * using the live calibrated rotation matrix maintained by [CelestialCalibrator].
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
 *  2. World vector × calibrated device-orientation matrix  →  device-space vector
 *  3. Objects behind the camera (deviceVector[2] ≥ 0) are culled (return null)
 *  4. Perspective divide with a fixed focal factor  →  screen pixel coordinates
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

        val worldVector = floatArrayOf(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat(), 1.0f)
        val deviceVector = FloatArray(4)

        // ── Step 2: Apply the calibrated device-orientation matrix ──
        Matrix.multiplyMV(deviceVector, 0, calibrator.calibratedMatrix, 0, worldVector, 0)

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
