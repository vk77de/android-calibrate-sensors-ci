package com.example.helloworldkotlinandroid

import android.graphics.PointF
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// --- Color Configuration Map ---
val BodyColors =
    mapOf(
        "Moon" to Color(0xFFEAEAEA), "Venus" to Color(0xFFFFFFFF), "Jupiter" to Color(0xFFFFFDD0),
        "Mars" to Color(0xFFFF5733), "Sirius" to Color(0xFFE0FFFF), "Saturn" to Color(0xFFFFD700),
        "Arcturus" to Color(0xFFFF8C00),
        "Canopus" to Color(0xFFF0F8FF),
        "Alpha Centauri" to Color(0xFFFFFFE0),
        "Vega" to Color(0xFFF4F8FF), "Capella" to Color(0xFFFFFACD), "Rigel" to Color(0xFFB0E0E6),
        "Procyon" to Color(0xFFFFF8DC),
        "Achernar" to Color(0xFFD4E6F1), "Betelgeuse" to Color(0xFFFF4500),
        "Altair" to Color(0xFFF5F5F5),
        "Aldebaran" to Color(0xFFFA8072), "Sagittarius A*" to Color(0xFFDA70D6),
        "Great Attractor" to Color(0xFFFF1493), "Shapley Attractor" to Color(0xFFFF00FF),
        "Dipole Repeller" to Color(0xFF00FFFF), "Cold Spot Repeller" to Color(0xFF1E90FF)
    )

// --- Radii Configuration Map ---
val BodyRadii =
    mapOf(
        "Moon" to 26f, "Venus" to 14f, "Jupiter" to 18f, "Mars" to 12f, "Sirius" to 10f,
        "Saturn" to 15f, "Arcturus" to 10f, "Canopus" to 9f, "Alpha Centauri" to 9f, "Vega" to 9f,
        "Capella" to 8f, "Rigel" to 9f, "Procyon" to 8f, "Achernar" to 8f, "Betelgeuse" to 11f,
        "Altair" to 7f, "Aldebaran" to 9f, "Sagittarius A*" to 14f, "Great Attractor" to 16f,
        "Shapley Attractor" to 15f, "Dipole Repeller" to 14f, "Cold Spot Repeller" to 14f
    )

@Composable
fun CameraXPreview(modifier: Modifier = Modifier, onPreviewViewCreated: (PreviewView) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier,
        update = { viewFinder ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview =
                    Preview.Builder().build().also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun CelestialOverlayCanvas(
    calibrator: CelestialCalibrator,
    latitude: Double,
    longitude: Double,
// Mutation driving real-time state invalidation loops
    frameTicker: Long,
    modifier: Modifier = Modifier
) {
    val textPaint =
        remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 32f
                isAntiAlias = true
            }
        }

    if (latitude == 0.0 && longitude == 0.0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width.toInt()
        val height = size.height.toInt()

        // Supplying ticker hook explicitly isolates frame mutations safely within our block
        val currentFrame = frameTicker

        val bodies = CelestialObjectsCalculator.getCalibratedObjects(latitude, longitude)

        for (body in bodies) {
            val screenPoint: PointF? =
                calibrator.projectOrientationToScreen(
                    body.azimuth,
                    body.altitude,
                    width,
                    height
                )

            if (screenPoint != null &&
                screenPoint.x >= 0 && screenPoint.x <= width &&
                screenPoint.y >= 0 && screenPoint.y <= height
            ) {
                val color = BodyColors[body.name] ?: Color.White
                val radius = BodyRadii[body.name] ?: 10f

                drawCircle(
                    color = color,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(screenPoint.x, screenPoint.y)
                )

                drawContext.canvas.nativeCanvas.drawText(
                    body.name,
                    screenPoint.x + radius + 8f,
                    screenPoint.y + 10f,
                    textPaint
                )
            }
        }
    }
}

@Composable
fun TelemetryOverlay(
    metadata: String,
    lat: Double,
    lon: Double,
    targetAz: Double,
    targetAlt: Double,
    offsetAz: Float,
    offsetPitch: Float,
    offsetRoll: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Text(
            text =
            String.format(
                """
                    %s
                    
                    --- GPS TELEMETRY ---
                    Lat: %.6f
                    Lon: %.6f
                    
                    --- MOON POSITION ---
                    Target Az:  %.2f°
                    Target Alt: %.2f°
                    
                    --- ACTIVE CALIBRATION ---
                    Offset Az:   %.2f°
                    Offset Pitch: %.2f°
                    Offset Roll:  %.2f°
                """.trimIndent(),
                metadata, lat, lon, targetAz, targetAlt, offsetAz, offsetPitch, offsetRoll
            ),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
