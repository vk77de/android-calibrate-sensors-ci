package com.example.helloworldkotlinandroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val celestialCalibrator = CelestialCalibrator()
    private lateinit var storageManager: CalibrationStorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // ---- CRASH LOGGER PATCH START ----
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val crashFile = File(getExternalFilesDir(null), "crash_dump.txt")
                PrintWriter(FileWriter(crashFile)).use { throwable.printStackTrace(it) }
            } catch (e: Exception) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }
        // ---- CRASH LOGGER PATCH END ----

        super.onCreate(savedInstanceState)
        storageManager = CalibrationStorageManager(this)

        setContent {
            var hasPermissions by remember { mutableStateOf(allPermissionsGranted()) }

            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasPermissions = permissions.values.all { it }
                    if (!hasPermissions) {
                        Toast.makeText(
                            this,
                            "Camera and Location permissions are required.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }

            LaunchedEffect(Unit) {
                if (!hasPermissions) {
                    launcher.launch(REQUIRED_PERMISSIONS)
                }
            }

            if (hasPermissions) {
                CelestialTrackerScreen(celestialCalibrator, storageManager)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
    }
}

@Composable
fun CelestialTrackerScreen(
    calibrator: CelestialCalibrator,
    storageManager: CalibrationStorageManager
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    // --- State Vectors ---
    var deviceLatitude by remember { mutableStateOf(0.0) }
    var deviceLongitude by remember { mutableStateOf(0.0) }
    var currentAzimuthOffset by remember { mutableStateOf(0.0f) }
    var currentPitchOffset by remember { mutableStateOf(0.0f) }
    var currentRollOffset by remember { mutableStateOf(0.0f) }
    var versionMetadata by remember { mutableStateOf("Version metadata unavailable") }
    var frameTicker by remember { mutableStateOf(0L) }

    // --- High-Performance Compose Invalidations Loop ---
    LaunchedEffect(Unit) {
        while (true) {
            frameTicker++
            delay(16L) // Matches standard ~60fps rendering frame cycles
        }
    }

    // --- Version Extraction ---
    LaunchedEffect(Unit) {
        try {
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            versionMetadata = "App Version: $versionName (Build: $versionCode)"
        } catch (e: Exception) {
        }
    }

    // --- Loading Historical Storage Matrices ---
    LaunchedEffect(Unit) {
        storageManager.readLatestCalibration()?.let { saved ->
            currentAzimuthOffset = saved.azimuthOffset
            currentPitchOffset = saved.pitchOffset
            currentRollOffset = saved.rollOffset
            calibrator.setCalibrationOffsets(
                saved.azimuthOffset,
                saved.pitchOffset,
                saved.rollOffset
            )
        }
    }

    // --- Hardware Sensor Pipeline Mounting ---
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotVectorSensor == null) {
            Toast.makeText(
                context,
                "Rotation Vector Sensor missing on hardware!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            sensorManager.registerListener(
                calibrator,
                rotVectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        onDispose {
            sensorManager.unregisterListener(calibrator)
        }
    }

    // --- Location Pipeline Mounting ---
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    deviceLatitude = location.latitude
                    deviceLongitude = location.longitude
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {}

                override fun onProviderDisabled(provider: String) {}
            }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                5f,
                locationListener
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                5f,
                locationListener
            )

            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            (lastGps ?: lastNet)?.let {
                deviceLatitude = it.latitude
                deviceLongitude = it.longitude
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location access tracing failure.", Toast.LENGTH_SHORT).show()
        }

        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    // --- Navigation Layout ---
    NavHost(navController = navController, startDestination = "planetarium") {
        composable("planetarium") {
            PlanetariumScreen(
                calibrator = calibrator,
                latitude = deviceLatitude,
                longitude = deviceLongitude,
                frameTicker = frameTicker,
                onNavigateToCalibration = {
                    navController.navigate("calibration")
                }
            )
        }
        composable("calibration") {
            val moonTarget = remember(deviceLatitude, deviceLongitude, frameTicker) {
                MoonCalculator.getPosition(deviceLatitude, deviceLongitude)
            }

            CalibrationScreen(
                calibrator = calibrator,
                storageManager = storageManager,
                latitude = deviceLatitude,
                longitude = deviceLongitude,
                frameTicker = frameTicker,
                versionMetadata = versionMetadata,
                moonTarget = moonTarget,
                currentAzimuthOffset = currentAzimuthOffset,
                currentPitchOffset = currentPitchOffset,
                currentRollOffset = currentRollOffset,
                onUpdateOffsets = { az, pitch, roll ->
                    currentAzimuthOffset = az
                    currentPitchOffset = pitch
                    currentRollOffset = roll
                },
                onNavigateToPlanetarium = {
                    navController.navigate("planetarium") {
                        popUpTo("planetarium") { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun PlanetariumScreen(
    calibrator: CelestialCalibrator,
    latitude: Double,
    longitude: Double,
    frameTicker: Long,
    onNavigateToCalibration: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camera Underlay
        CameraXPreview(
            modifier = Modifier.fillMaxSize(),
            onPreviewViewCreated = { _ -> }
        )

        // 2. Dynamic Stars/Planets Overlay (Forced to Render on top with zIndex)
        CelestialOverlayCanvas(
            calibrator = calibrator,
            latitude = latitude,
            longitude = longitude,
            frameTicker = frameTicker,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        )

        // 3. Small red reticle icon in the upper right hand corner to switch to calibration mode
        ReticleIcon(
            onClick = onNavigateToCalibration,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(2f)
        )
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
    // Keep dynamically generated type safety
    moonTarget: MoonCalculator.Position,
    currentAzimuthOffset: Float,
    currentPitchOffset: Float,
    currentRollOffset: Float,
    onUpdateOffsets: (Float, Float, Float) -> Unit,
    onNavigateToPlanetarium: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (latitude == 0.0 && longitude == 0.0) {
                    Toast.makeText(
                        context,
                        "Acquiring fresh GPS lock... try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val target = MoonCalculator.getPosition(latitude, longitude)
                    val offsets =
                        calibrator.performCelestialCalibration(
                            target.azimuth.toFloat(),
                            target.altitude.toFloat()
                        )

                    onUpdateOffsets(offsets[0], offsets[1], offsets[2])

                    storageManager.writeCalibrationToAllStorages(
                        MoonCalibrationData(
                            timestamp = System.currentTimeMillis(),
                            azimuthOffset = offsets[0],
                            pitchOffset = offsets[1],
                            rollOffset = offsets[2]
                        )
                    )

                    Toast.makeText(
                        context,
                        String.format(
                            "Calibrated on Moon!\nAz: %.2f° | Alt: %.2f°",
                            target.azimuth,
                            target.altitude
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    ) {
        // 1. Camera Underlay
        CameraXPreview(
            modifier = Modifier.fillMaxSize(),
            onPreviewViewCreated = { _ -> }
        )

        // 2. Dynamic Stars/Planets Overlay (Forced to Render on top with zIndex)
        CelestialOverlayCanvas(
            calibrator = calibrator,
            latitude = latitude,
            longitude = longitude,
            frameTicker = frameTicker,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        )

        // 3. Central Guiding Reticle
        ReticleOverlay(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(2f)
        )

        // 4. Moon Icon in top-right corner to return to the Planetarium mode
        MoonIcon(
            onClick = onNavigateToPlanetarium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(2f)
        )

        // 5. Diagnostics Telemetry Block
        TelemetryOverlay(
            metadata = versionMetadata,
            lat = latitude,
            lon = longitude,
            targetAz = moonTarget.azimuth,
            targetAlt = moonTarget.altitude,
            offsetAz = currentAzimuthOffset,
            offsetPitch = currentPitchOffset,
            offsetRoll = currentRollOffset,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(2f)
        )
    }
}
