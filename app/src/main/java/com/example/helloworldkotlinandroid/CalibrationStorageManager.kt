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

    fun writeCalibrationToAllStorages(data: MoonCalibrationData): Boolean {
        val payload = data.toJsonString()
        val internalSuccess = saveToInternalStorage(payload)
        val sdCardSuccess = saveToPhysicalSdCard(payload)

        if (internalSuccess && sdCardSuccess) {
            Log.d(TAG, "Calibration data mirrored safely to both filesystems.")
        }
        return internalSuccess
    }

    fun readLatestCalibration(): MoonCalibrationData? {
        val targetFile = File(context.filesDir, FILE_NAME)
        if (!targetFile.exists()) return null

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

    private fun saveToInternalStorage(payload: String): Boolean {
        return try {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { output ->
                output.write(payload.toByteArray())
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun saveToPhysicalSdCard(payload: String): Boolean {
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size < 2 || externalDirs[1] == null) return false

        val physicalSdCardDir = externalDirs[1]!!
        if (!physicalSdCardDir.exists() && !physicalSdCardDir.mkdirs()) return false

        val targetFile = File(physicalSdCardDir, FILE_NAME)
        return try {
            FileOutputStream(targetFile).use { output ->
                output.write(payload.toByteArray())
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}

