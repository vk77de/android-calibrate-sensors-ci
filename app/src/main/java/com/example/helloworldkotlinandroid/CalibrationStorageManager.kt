package com.example.helloworldkotlinandroid

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class MoonCalibrationData(
    val timestamp: Long,
    val azimuthOffset: Float,
    val pitchOffset: Float,
    val rollOffset: Float,
    val targetCelestialBody: String = "Moon",
) {
    // Converts the calibration metrics into a clean JSON layout
    fun toJsonString(): String {
        return """
            {
                "target": "$targetCelestialBody",
                "timestamp": $timestamp,
                "azimuth_offset": $azimuthOffset,
                "pitch_offset": $pitchOffset,
                "roll_offset": $rollOffset
            }
            """.trimIndent()
    }
}

class CalibrationStorageManager(private val context: Context) {
    companion object {
        private const val TAG = "CalibrationStorage"
        private const val FILE_NAME = "moon_sensor_calibration.json"
    }

    /**
     * Executes saving operations to both filesystems simultaneously.
     */
    fun writeCalibrationToAllStorages(data: MoonCalibrationData) {
        val payload = data.toJsonString()

        // 1. Save to Internal Private Filesystem
        val internalSuccess = saveToInternalStorage(payload)

        // 2. Save to Physical Removable SD Card Filesystem
        val sdCardSuccess = saveToPhysicalSdCard(payload)

        if (internalSuccess && sdCardSuccess) {
            Log.d(TAG, "Calibration data mirrored safely to both filesystems.")
        }
    }

    /**
     * Reads the latest saved calibration data from internal private storage.
     * Parses the JSON payload back into a MoonCalibrationData object.
     * @return MoonCalibrationData instance if successful, or null if file doesn't exist or is corrupted.
     */
    fun readLatestCalibration(): MoonCalibrationData? {
        val targetFile = File(context.filesDir, FILE_NAME)

        if (!targetFile.exists()) {
            Log.d(TAG, "No historical calibration file found at ${targetFile.absolutePath}")
            return null
        }

        return try {
            val jsonString = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            MoonCalibrationData(
                timestamp = jsonObject.getLong("timestamp"),
                azimuthOffset = jsonObject.getDouble("azimuth_offset").toFloat(),
                pitchOffset = jsonObject.getDouble("pitch_offset").toFloat(),
                rollOffset = jsonObject.getDouble("roll_offset").toFloat(),
                targetCelestialBody = jsonObject.optString("target", "Moon"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure reading or decoding calibration JSON payload", e)
            null
        }
    }

    /**
     * Writes directly to the app's private data sandbox: /data/user/0/[package]/files
     * No permissions required. Survives app updates. Completely secure.
     */
    private fun saveToInternalStorage(payload: String): Boolean {
        return try {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { output ->
                output.write(payload.toByteArray())
            }
            Log.d(TAG, "Successfully written to internal memory: ${context.filesDir}/$FILE_NAME")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Critical failure writing to internal storage", e)
            false
        }
    }

    /**
     * Finds and writes to the app's isolated directory residing on the hardware SD card.
     * Android 14 allows permissionless writes directly to context.getExternalFilesDirs paths.
     */
    private fun saveToPhysicalSdCard(payload: String): Boolean {
        // Returns all available external storage paths assigned to the app.
        // Index [0] is standard internal shared storage (/storage/emulated/0).
        // Index [1] (if present) represents the physical, removable micro-SD Card.
        val externalDirs: Array<File?> = context.getExternalFilesDirs(null)

        if (externalDirs.size < 2 || externalDirs[1] == null) {
            Log.w(TAG, "SD Card write skipped: Secondary physical storage media not detected.")
            return false
        }

        val physicalSdCardDir = externalDirs[1]!!

        // Ensure the directory structure exists on the external card mount point
        if (!physicalSdCardDir.exists() && !physicalSdCardDir.mkdirs()) {
            Log.e(TAG, "Failed to build target directory tree on the physical SD Card.")
            return false
        }

        val targetFile = File(physicalSdCardDir, FILE_NAME)

        return try {
            FileOutputStream(targetFile).use { output ->
                output.write(payload.toByteArray())
            }
            Log.d(TAG, "Successfully written to removable SD Card: ${targetFile.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Critical failure writing to physical SD Card filesystem", e)
            false
        }
    }
}
