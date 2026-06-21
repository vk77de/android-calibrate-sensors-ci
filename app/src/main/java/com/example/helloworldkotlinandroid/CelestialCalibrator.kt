import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix

class CelestialCalibrator : SensorEventListener {

    // Matrices stored as 16-element 4x4 flat arrays for OpenGL compatibility
    private val rawSensorMatrix = FloatArray(16)
    private val calibrationOffsetMatrix = FloatArray(16)
    private val calibratedMatrix = FloatArray(16)
    private var isCalibrated = false

    init {
        // Initialize the calibration matrix as an Identity Matrix (no offset initially)
        Matrix.setIdentityM(calibrationOffsetMatrix, 0)
    }

    /**
     * Call this exact method when the user aligns a known object 
     * (e.g. Jupiter) perfectly in the center crosshair of the camera view.
     * @param trueAzimuth In degrees from North (0 to 360)
     * @param trueAltitude In degrees from Horizon (-90 to 90)
     */
    fun performCelestialCalibration(trueAzimuth: Float, trueAltitude: Float) {
        val trueRotationMatrix = FloatArray(16)
        
        // Convert the mathematically calculated real-world coordinates into a target matrix
        Matrix.setIdentityM(trueRotationMatrix, 0)
        Matrix.rotateM(trueRotationMatrix, 0, -trueAzimuth, 0f, 1f, 0f)  // Y-axis rotation
        Matrix.rotateM(trueRotationMatrix, 0, trueAltitude, 1f, 0f, 0f)  // X-axis rotation

        // Invert the current inaccurate sensor matrix
        val invertedSensorMatrix = FloatArray(16)
        Matrix.invertM(invertedSensorMatrix, 0, rawSensorMatrix, 0)

        // Calculate the error delta offset: Offset = True * Sensor^-1
        Matrix.multiplyMM(calibrationOffsetMatrix, 0, trueRotationMatrix, 0, invertedSensorMatrix, 0)
        isCalibrated = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Get the raw sensor fusion matrix directly from Android OS
            SensorManager.getRotationMatrixFromVector(rawSensorMatrix, event.values)

            if (isCalibrated) {
                // Apply our correction matrix to the live sensor feed
                Matrix.multiplyMM(calibratedMatrix, 0, calibrationOffsetMatrix, 0, rawSensorMatrix, 0)
                // Use 'calibratedMatrix' to update your 3D star grid orientation
            } else {
                // Fallback to uncalibrated data if user hasn't calibrated yet
                System.arraycopy(rawSensorMatrix, 0, calibratedMatrix, 0, 16)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
