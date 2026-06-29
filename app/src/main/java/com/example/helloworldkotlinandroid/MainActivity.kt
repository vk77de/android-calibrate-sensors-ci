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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var viewFinder: PreviewView
    private lateinit var debugTelemetry: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private lateinit var celestialCalibrator: CelestialCalibrator
    private var versionMetadata: String = ""

    private lateinit var locationManager: LocationManager
    private var deviceLatitude: Double = 0.0
    private var deviceLongitude: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize all views first
        debugTelemetry = findViewById(R.id.debugTelemetry)
        viewFinder = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        celestialCalibrator = CelestialCalibrator()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 2. Cache and render the metadata immediately
        displayAppMetadata()

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Rotation Vector Sensor missing on this hardware!", Toast.LENGTH_LONG).show()
        }

        viewFinder.setOnClickListener {
            // Guard conditions check for initial telemetry tracking safety
            if (deviceLatitude == 0.0 && deviceLongitude == 0.0) {
                Toast.makeText(
                    this,
                    "Acquiring fresh device GPS lock... try again in a moment.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                val currentEpochMs = System.currentTimeMillis()

                // Solve topocentric position vectors for the Moon
                val moonTarget =
                    MoonCalculator.getMoonPosition(
                        deviceLatitude,
                        deviceLongitude,
                        currentEpochMs,
                    )

                // Pass the resolved celestial path arrays straight to the matrix transformer
                celestialCalibrator.performCelestialCalibration(
                    moonTarget.azimuth,
                    moonTarget.altitude,
                )

                val telemetryReport =
                    String.format(
                        "Calibrated on Moon!\nAz: %.2f° | Alt: %.2f°",
                        moonTarget.azimuth,
                        moonTarget.altitude,
                    )
                Toast.makeText(this, telemetryReport, Toast.LENGTH_LONG).show()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
            setupLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun displayAppMetadata() {
        try {
            val packageInfo =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }

            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

            // Cache the string safely for the telemetry loop
            versionMetadata = "App Version: $versionName (Build: $versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            versionMetadata = "Version metadata unavailable"
        }

        // Initial draw to print metadata immediately on launch
        updateDebugDisplay()
    }

    private fun updateDebugDisplay() {
        val moonTarget = MoonCalculator.getPosition(deviceLatitude, deviceLongitude)

        // Prepend the cached metadata string so it never disappears
        debugTelemetry.text =
            String.format(
                """
                %s
                
                --- GPS TELEMETRY ---
                Lat: %.6f
                Lon: %.6f
                
                --- MOON POSITION ---
                Target Az:  %.2f°
                Target Alt: %.2f°
                """.trimIndent(),
                versionMetadata, deviceLatitude, deviceLongitude, moonTarget.azimuth, moonTarget.altitude,
            )
    }

    private fun setupLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)

                val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLocation = lastKnownGps ?: lastKnownNetwork

                bestLocation?.let {
                    deviceLatitude = it.latitude
                    deviceLongitude = it.longitude

                    // Bugfix: Instantly update the UI with last known telemetry on startup/resume
                    updateDebugDisplay()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location access tracing security failure.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        deviceLatitude = location.latitude
        deviceLongitude = location.longitude
        updateDebugDisplay()
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.let {
            sensorManager.registerListener(
                celestialCalibrator,
                it,
                SensorManager.SENSOR_DELAY_UI,
            )
        }
        if (allPermissionsGranted()) {
            setupLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(celestialCalibrator)
        locationManager.removeUpdates(this)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to bind camera use cases.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                setupLocationUpdates()
            } else {
                Toast.makeText(
                    this,
                    "Camera and Location permissions are required for celestial tracking.",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
    }
}
