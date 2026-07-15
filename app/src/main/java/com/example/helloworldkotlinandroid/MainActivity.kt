package com.example.helloworldkotlinandroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlinx.coroutines.delay

/**
 * A SensorEventListener wrapper that filters incoming sensor events
 * using an Exponential Moving Average (EMA) to prevent visual jitter.
 */
class SmoothedSensorEventListener(
    private val delegate: SensorEventListener,
    // Adjust between 0.05
    // (slower/smoother) and
    // 0.25 (faster/jitterier)
    private val alpha: Float = 0.12f
) : SensorEventListener {
    private var smoothedValues: FloatArray? = null

    override fun onSensorChanged(event: SensorEvent) {
        val currentValues = event.values
        var smoothed = smoothedValues

        if (smoothed == null || smoothed.size != currentValues.size) {
            smoothed = currentValues.clone()
            smoothedValues = smoothed
        } else {
            for (i in currentValues.indices) {
                smoothed[i] = alpha * currentValues[i] + (1f - alpha) * smoothed[i]
            }
        }

        // Mutate values array with smoothed data and delegate downstream
        System.arraycopy(smoothed, 0, event.values, 0, smoothed.size)
        delegate.onSensorChanged(event)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        delegate.onAccuracyChanged(sensor, accuracy)
    }
}

class MainActivity : ComponentActivity() {
    private val celestialCalibrator = CelestialCalibrator()
    private lateinit var storageManager: CalibrationStorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val crashFile = File(getExternalFilesDir(null), "crash_dump.txt")
                PrintWriter(FileWriter(crashFile)).use { throwable.printStackTrace(it) }
            } catch (e: Exception) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }

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

    var deviceLatitude by remember { mutableStateOf(0.0) }
    var deviceLongitude by remember { mutableStateOf(0.0) }
    var currentAzimuthOffset by remember { mutableStateOf(0.0f) }
    var currentPitchOffset by remember { mutableStateOf(0.0f) }
    var currentRollOffset by remember { mutableStateOf(0.0f) }
    var versionMetadata by remember { mutableStateOf("Version metadata unavailable") }
    var frameTicker by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            frameTicker++
            delay(16L) // ~60fps
        }
    }

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

    // --- Hardware Sensor Pipeline with Filtering ---
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var smoothedListener: SmoothedSensorEventListener? = null

        if (rotVectorSensor == null) {
            Toast.makeText(
                context,
                "Rotation Vector Sensor missing on hardware!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Smooth raw movements using the custom EMA wrapper
            smoothedListener = SmoothedSensorEventListener(calibrator, alpha = 0.12f)

            // SENSOR_DELAY_GAME provides faster updates (~20ms) which makes the filtering much cleaner
            sensorManager.registerListener(
                smoothedListener,
                rotVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            smoothedListener?.let {
                sensorManager.unregisterListener(it)
            }
        }
    }

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
