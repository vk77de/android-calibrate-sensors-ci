package com.example.helloworldkotlinandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View

class CelestialOverlayView(
    context: Context,
    private val calibrator: CelestialCalibrator,
) : View(context), Choreographer.FrameCallback {

    private var lat: Double = 0.0
    private var lon: Double = 0.0

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
    }

    private val bodyPaints = mapOf(
        "Venus" to Paint().apply { color = Color.parseColor("#FFFFFF"); isAntiAlias = true },
        "Jupiter" to Paint().apply { color = Color.parseColor("#FFFDD0"); isAntiAlias = true },
        "Mars" to Paint().apply { color = Color.parseColor("#FF5733"); isAntiAlias = true },
        "Sirius" to Paint().apply { color = Color.parseColor("#E0FFFF"); isAntiAlias = true },
        "Saturn" to Paint().apply { color = Color.parseColor("#FFD700"); isAntiAlias = true },
        "Arcturus" to Paint().apply { color = Color.parseColor("#FF8C00"); isAntiAlias = true }
    )

    private val bodyRadii = mapOf(
        "Venus" to 14f,
        "Jupiter" to 18f,
        "Mars" to 12f,
        "Sirius" to 8f,
        "Saturn" to 15f,
        "Arcturus" to 10f
    )

    init {
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun updateCoordinates(latitude: Double, longitude: Double) {
        lat = latitude
        lon = longitude
    }

    override fun doFrame(frameTimeNanos: Long) {
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lat == 0.0 && lon == 0.0) return

        val bodies = CelestialObjectsCalculator.getCalibratedObjects(lat, lon)

        for (body in bodies) {
            val screenPoint = calibrator.projectOrientationToScreen(
                body.azimuth,
                body.altitude,
                width,
                height
            )

            if (screenPoint != null && screenPoint.x >= 0 && screenPoint.x <= width && screenPoint.y >= 0 && screenPoint.y <= height) {
                val paint = bodyPaints[body.name] ?: textPaint
                val radius = bodyRadii[body.name] ?: 10f

                canvas.drawCircle(
                    screenPoint.x,
                    screenPoint.y,
                    radius,
                    paint
                )

                canvas.drawText(
                    body.name,
                    screenPoint.x + radius + 8f,
                    screenPoint.y + 10f,
                    textPaint
                )
            }
        }
    }
}