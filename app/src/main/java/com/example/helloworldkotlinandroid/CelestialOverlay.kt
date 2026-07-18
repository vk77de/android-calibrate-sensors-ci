package com.example.helloworldkotlinandroid

import android.graphics.PointF
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// --- Color Configuration Map ---
val BodyColors =
    mapOf(
        "Moon" to Color(0xFFEAEAEA), "Sun" to Color(0xFFFFB300),
        "Venus" to Color(0xFFFFFFFF), "Jupiter" to Color(0xFFFFFDD0),
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
        "Moon" to 26f, "Sun" to 30f, "Venus" to 14f,
        "Jupiter" to 18f, "Mars" to 12f, "Sirius" to 10f,
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

    val effectiveLatitude = if (latitude == 0.0 && longitude == 0.0) 50.1109 else latitude
    val effectiveLongitude = if (latitude == 0.0 && longitude == 0.0) 8.6821 else longitude

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width.toInt()
        val height = size.height.toInt()

        if (frameTicker < 0L) {
            drawCircle(Color.Transparent, 0f)
        }

        val bodies = CelestialObjectsCalculator.getCalibratedObjects(
            effectiveLatitude,
            effectiveLongitude
        )

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
    targetName: String,
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
                    
                    --- %s POSITION ---
                    Target Az:  %.2f°
                    Target Alt: %.2f°
                    
                    --- ACTIVE CALIBRATION ---
                    Offset Az:   %.2f°
                    Offset Pitch: %.2f°
                    Offset Roll:  %.2f°
                """.trimIndent(),
                metadata, lat, lon, targetName.uppercase(), targetAz,
                targetAlt, offsetAz, offsetPitch, offsetRoll
            ),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun ReticleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(300.dp)) {
        val scaleX = size.width / 100f
        val scaleY = size.height / 100f

        scale(scaleX, scaleY, pivot = Offset.Zero) {
            drawCircle(
                color = Color.Red,
                radius = 35f,
                center = Offset(50f, 50f),
                style = Stroke(width = 0.6f)
            )

            drawCircle(
                color = Color.Red,
                radius = 18f,
                center = Offset(50f, 50f),
                style = Stroke(width = 0.4f)
            )

            drawLine(
                color = Color.Red,
                start = Offset(48f, 0f),
                end = Offset(48f, 100f),
                strokeWidth = 0.5f
            )
            drawLine(
                color = Color.Red,
                start = Offset(52f, 0f),
                end = Offset(52f, 100f),
                strokeWidth = 0.5f
            )

            drawLine(
                color = Color.Red,
                start = Offset(0f, 48f),
                end = Offset(100f, 48f),
                strokeWidth = 0.5f
            )
            drawLine(
                color = Color.Red,
                start = Offset(0f, 52f),
                end = Offset(100f, 52f),
                strokeWidth = 0.5f
            )
        }
    }
}

@Composable
fun ReticleIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(
                color = Color.Red,
                radius = radius * 0.8f,
                style = Stroke(width = 2f)
            )

            drawCircle(
                color = Color.Red,
                radius = radius * 0.4f,
                style = Stroke(width = 1.5f)
            )

            drawLine(
                color = Color.Red,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1.5f
            )

            drawLine(
                color = Color.Red,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
fun MoonIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.65f, size.height * 0.15f)
                cubicTo(
                    size.width * 0.15f,
                    size.height * 0.2f,
                    size.width * 0.15f,
                    size.height * 0.8f,
                    size.width * 0.65f,
                    size.height * 0.85f
                )
                cubicTo(
                    size.width * 0.4f,
                    size.height * 0.7f,
                    size.width * 0.4f,
                    size.height * 0.3f,
                    size.width * 0.65f,
                    size.height * 0.15f
                )
                close()
            }
            drawPath(path = path, color = Color(0xFFEAEAEA))
        }
    }
}

@Composable
fun VenusIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(
                color = Color(0xFFFFF9E6),
                radius = radius * 0.8f
            )
        }
    }
}

@Composable
fun SunIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            drawCircle(
                color = Color(0xFFFFB300),
                radius = radius * 0.8f
            )
        }
    }
}

private fun getDoubleProperty(obj: Any?, propName: String): Double {
    if (obj == null) return 0.0
    return try {
        val capitalized = propName.substring(0, 1).uppercase() + propName.substring(1)
        val getterName = "get$capitalized"
        val method = obj.javaClass.methods.firstOrNull {
            it.name == getterName || it.name == propName
        }
        val value = method?.invoke(obj) ?: 0.0
        (value as Number).toDouble()
    } catch (e: Exception) {
        0.0
    }
}

@Composable
fun CalibrationSelectionScreen(
    onSelectTarget: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraXPreview(modifier = Modifier.fillMaxSize()) { _ -> }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CALIBRATION METHOD",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Align reticle with your chosen object to sync phone sensors.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Button 1: Moon Calibration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
                    .clickable { onSelectTarget("Moon") }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1. Moon Calibration",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Button 2: Venus Calibration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
                    .clickable { onSelectTarget("Venus") }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "2. Venus Calibration",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Button 3: Sun Calibration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.15f), shape = CircleShape)
                    .clickable { onSelectTarget("Sun") }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "3. Sun Calibration",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Cancel/Back
            Box(
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.4f), shape = CircleShape)
                    .clickable { onNavigateBack() }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CalibrationScreen(
    calibrator: CelestialCalibrator,
    storageManager: CalibrationStorageManager,
    latitude: Double,
    longitude: Double,
    frameTicker: Long,
    versionMetadata: String,
    moonTarget: Any?,
    currentAzimuthOffset: Float,
    currentPitchOffset: Float,
    currentRollOffset: Float,
    onUpdateOffsets: (Float, Float, Float) -> Unit,
    onNavigateToPlanetarium: () -> Unit,
    modifier: Modifier = Modifier,
    targetBodyName: String = "Moon"
) {
    val targetAz = getDoubleProperty(moonTarget, "azimuth")
    val targetAlt = getDoubleProperty(moonTarget, "altitude")

    Box(modifier = modifier.fillMaxSize()) {
        CameraXPreview(modifier = Modifier.fillMaxSize()) { _ -> }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        ) {
            ReticleOverlay()
        }

        TelemetryOverlay(
            metadata = versionMetadata,
            lat = latitude,
            lon = longitude,
            targetName = targetBodyName,
            targetAz = targetAz,
            targetAlt = targetAlt,
            offsetAz = currentAzimuthOffset,
            offsetPitch = currentPitchOffset,
            offsetRoll = currentRollOffset,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onNavigateToPlanetarium() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "← Back",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            when (targetBodyName) {
                "Venus" -> {
                    VenusIcon(
                        onClick = {
                            val offsets = calibrator.performCelestialCalibration(
                                targetAz.toFloat(),
                                targetAlt.toFloat()
                            )
                            val data = MoonCalibrationData(
                                timestamp = System.currentTimeMillis(),
                                azimuthOffset = offsets[0],
                                pitchOffset = offsets[1],
                                rollOffset = offsets[2],
                                targetCelestialBody = "Venus"
                            )
                            val success = storageManager.writeCalibrationToAllStorages(data)
                            if (success) {
                                onUpdateOffsets(offsets[0], offsets[1], offsets[2])
                            }
                        },
                        modifier = Modifier.background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                    )
                }
                "Sun" -> {
                    SunIcon(
                        onClick = {
                            val offsets = calibrator.performCelestialCalibration(
                                targetAz.toFloat(),
                                targetAlt.toFloat()
                            )
                            val data = MoonCalibrationData(
                                timestamp = System.currentTimeMillis(),
                                azimuthOffset = offsets[0],
                                pitchOffset = offsets[1],
                                rollOffset = offsets[2],
                                targetCelestialBody = "Sun"
                            )
                            val success = storageManager.writeCalibrationToAllStorages(data)
                            if (success) {
                                onUpdateOffsets(offsets[0], offsets[1], offsets[2])
                            }
                        },
                        modifier = Modifier.background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                    )
                }
                else -> {
                    MoonIcon(
                        onClick = {
                            val offsets = calibrator.performCelestialCalibration(
                                targetAz.toFloat(),
                                targetAlt.toFloat()
                            )
                            val data = MoonCalibrationData(
                                timestamp = System.currentTimeMillis(),
                                azimuthOffset = offsets[0],
                                pitchOffset = offsets[1],
                                rollOffset = offsets[2],
                                targetCelestialBody = "Moon"
                            )
                            val success = storageManager.writeCalibrationToAllStorages(data)
                            if (success) {
                                onUpdateOffsets(offsets[0], offsets[1], offsets[2])
                            }
                        },
                        modifier = Modifier.background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                    )
                }
            }
        }
    }
}
