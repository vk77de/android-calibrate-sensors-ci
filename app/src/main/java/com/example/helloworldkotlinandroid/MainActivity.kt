package com.example.helloworldkotlinandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                getExternalFilesDir(null)?.let { dir ->
                    val crashFile = File(dir, "crash_dump.txt")
                    PrintWriter(FileWriter(crashFile)).use { throwable.printStackTrace(it) }
                }
            } catch (e: Exception) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }

        super.onCreate(savedInstanceState)
        storageManager = CalibrationStorageManager(this)

        setContent {
            var hasPermissions by remember { mutableStateOf(allPermissionsGranted()) }

            var hasStorageAccess by remember { mutableStateOf(hasAllFilesAccess()) }

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

            val storageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                hasStorageAccess = hasAllFilesAccess()
                if (!hasStorageAccess) {
                    Toast.makeText(
                        this,
                        "All-files access is required to save calibration data.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            LaunchedEffect(Unit) {
                if (!hasPermissions) {
                    launcher.launch(REQUIRED_PERMISSIONS)
                }
            }
            LaunchedEffect(hasPermissions) {
                if (hasPermissions && !hasStorageAccess) {
                    val intent = try {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    } catch (e: Exception) {
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    }
                    storageLauncher.launch(intent)
                }
            }

            if (hasPermissions && hasStorageAccess) {
                CelestialTrackerScreen(celestialCalibrator, storageManager)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

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
    var selectedCalibrationTarget by remember { mutableStateOf("Moon") }

    LaunchedEffect(Unit) {
        while (true) {
            frameTicker++
            delay(16L)
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
        } catch (e: Exception) {
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
                    navController.navigate("calibration_selection")
                }
            )
        }
        composable("calibration_selection") {
            CalibrationSelectionScreen(
                onSelectTarget = { target ->
                    selectedCalibrationTarget = target
                    navController.navigate("calibration")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("calibration") {
            val targetBody =
                remember(selectedCalibrationTarget, deviceLatitude, deviceLongitude, frameTicker) {
                    when (selectedCalibrationTarget) {
                        "Venus" -> CelestialObjectsCalculator.getVenusPosition(
                            deviceLatitude,
                            deviceLongitude
                        )
                        "Sun" -> CelestialObjectsCalculator.getSunPosition(
                            deviceLatitude,
                            deviceLongitude
                        )
                        else -> MoonCalculator.getPosition(deviceLatitude, deviceLongitude)
                    }
                }

            CalibrationScreen(
                calibrator = calibrator,
                storageManager = storageManager,
                latitude = deviceLatitude,
                longitude = deviceLongitude,
                frameTicker = frameTicker,
                versionMetadata = versionMetadata,
                moonTarget = targetBody,
                targetBodyName = selectedCalibrationTarget,
                currentAzimuthOffset = currentAzimuthOffset,
                currentPitchOffset = currentPitchOffset,
                currentRollOffset = currentRollOffset,
                onUpdateOffsets = { az: Float, pitch: Float, roll: Float ->
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
    onNavigateToCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        CameraXPreview(modifier = Modifier.fillMaxSize()) { _ -> }

        CelestialOverlayCanvas(
            calibrator = calibrator,
            latitude = latitude,
            longitude = longitude,
            frameTicker = frameTicker,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = BuildConfig.GIT_HASH,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            ReticleIcon(
                onClick = onNavigateToCalibration,
                modifier = Modifier.background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
            )
        }
    }
}
