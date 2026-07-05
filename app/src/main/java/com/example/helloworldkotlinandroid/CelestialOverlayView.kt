package com.example.helloworldkotlinandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class CelestialOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {
        lateinit var calibrator: CelestialCalibrator

        private var lat: Double = 0.0
        private var lon: Double = 0.0

        private val textPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = 32f
                isAntiAlias = true
            }

        private val bodyPaints =
            mapOf(
                "Moon" to
                    Paint().apply {
                        color = Color.parseColor("#EAEAEA")
                        isAntiAlias = true
                    },
                "Venus" to
                    Paint().apply {
                        color = Color.parseColor("#FFFFFF")
                        isAntiAlias = true
                    },
                "Jupiter" to
                    Paint().apply {
                        color = Color.parseColor("#FFFDD0")
                        isAntiAlias = true
                    },
                "Mars" to
                    Paint().apply {
                        color = Color.parseColor("#FF5733")
                        isAntiAlias = true
                    },
                "Sirius" to
                    Paint().apply {
                        color = Color.parseColor("#E0FFFF")
                        isAntiAlias = true
                    },
                "Saturn" to
                    Paint().apply {
                        color = Color.parseColor("#FFD700")
                        isAntiAlias = true
                    },
                "Arcturus" to
                    Paint().apply {
                        color = Color.parseColor("#FF8C00")
                        isAntiAlias = true
                    },
                // New Stars
                "Canopus" to
                    Paint().apply {
                        color = Color.parseColor("#F0F8FF")
                        isAntiAlias = true
                    },
                "Alpha Centauri" to
                    Paint().apply {
                        color = Color.parseColor("#FFFFE0")
                        isAntiAlias = true
                    },
                "Vega" to
                    Paint().apply {
                        color = Color.parseColor("#F4F8FF")
                        isAntiAlias = true
                    },
                "Capella" to
                    Paint().apply {
                        color = Color.parseColor("#FFFACD")
                        isAntiAlias = true
                    },
                "Rigel" to
                    Paint().apply {
                        color = Color.parseColor("#B0E0E6")
                        isAntiAlias = true
                    },
                "Procyon" to
                    Paint().apply {
                        color = Color.parseColor("#FFF8DC")
                        isAntiAlias = true
                    },
                "Achernar" to
                    Paint().apply {
                        color = Color.parseColor("#D4E6F1")
                        isAntiAlias = true
                    },
                "Betelgeuse" to
                    Paint().apply {
                        color = Color.parseColor("#FF4500")
                        isAntiAlias = true
                    },
                "Altair" to
                    Paint().apply {
                        color = Color.parseColor("#F5F5F5")
                        isAntiAlias = true
                    },
                "Aldebaran" to
                    Paint().apply {
                        color = Color.parseColor("#FA8072")
                        isAntiAlias = true
                    },
                // Galactic Structures & Attractors / Repellers
                "Sagittarius A*" to
                    Paint().apply {
                        color = Color.parseColor("#DA70D6")
                        isAntiAlias = true
                    },
                "Great Attractor" to
                    Paint().apply {
                        color = Color.parseColor("#FF1493")
                        isAntiAlias = true
                    },
                "Shapley Attractor" to
                    Paint().apply {
                        color = Color.parseColor("#FF00FF")
                        isAntiAlias = true
                    },
                "Dipole Repeller" to
                    Paint().apply {
                        color = Color.parseColor("#00FFFF")
                        isAntiAlias = true
                    },
                "Cold Spot Repeller" to
                    Paint().apply {
                        color = Color.parseColor("#1E90FF")
                        isAntiAlias = true
                    },
            )

        private val bodyRadii =
            mapOf(
                "Moon" to 26f,
                "Venus" to 14f,
                "Jupiter" to 18f,
                "Mars" to 12f,
                "Sirius" to 10f,
                "Saturn" to 15f,
                "Arcturus" to 10f,
                // Stars
                "Canopus" to 9f,
                "Alpha Centauri" to 9f,
                "Vega" to 9f,
                "Capella" to 8f,
                "Rigel" to 9f,
                "Procyon" to 8f,
                "Achernar" to 8f,
                "Betelgeuse" to 11f,
                "Altair" to 7f,
                "Aldebaran" to 9f,
                // Anomalies
                "Sagittarius A*" to 14f,
                "Great Attractor" to 16f,
                "Shapley Attractor" to 15f,
                "Dipole Repeller" to 14f,
                "Cold Spot Repeller" to 14f,
            )

        init {
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun updateCoordinates(
            latitude: Double,
            longitude: Double,
        ) {
            lat = latitude
            lon = longitude
        }

        override fun doFrame(frameTimeNanos: Long) {
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (lat == 0.0 && lon == 0.0 || !::calibrator.isInitialized) return

            val bodies = CelestialObjectsCalculator.getCalibratedObjects(lat, lon)

            for (body in bodies) {
                val screenPoint =
                    calibrator.projectOrientationToScreen(
                        body.azimuth,
                        body.altitude,
                        width,
                        height,
                    )

                if (screenPoint != null && screenPoint.x >= 0 && screenPoint.x <= width && screenPoint.y >= 0 && screenPoint.y <= height) {
                    val paint = bodyPaints[body.name] ?: textPaint
                    val radius = bodyRadii[body.name] ?: 10f

                    canvas.drawCircle(
                        screenPoint.x,
                        screenPoint.y,
                        radius,
                        paint,
                    )

                    canvas.drawText(
                        body.name,
                        screenPoint.x + radius + 8f,
                        screenPoint.y + 10f,
                        textPaint,
                    )
                }
            }
        }
    }
