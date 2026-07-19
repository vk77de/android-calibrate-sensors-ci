package com.example.helloworldkotlinandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONObject

data class MoonCalibrationData(
    val timestamp: Long,
    val azimuthOffset: Float,
    val pitchOffset: Float,
    val rollOffset: Float,
    val targetCelestialBody: String = "Moon",
    val dateTimeStamp: String = "N/A",
    val trueAzimuth: Float = Float.NaN,
    val trueRa: Float = Float.NaN,
    val yawAkaAzimuth: Float = Float.NaN,
    val pitch: Float = Float.NaN,
    val roll: Float = Float.NaN
) {
    fun toJsonString(): String {
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("target", targetCelestialBody)
            jsonObject.put("calibration_type", targetCelestialBody)
            jsonObject.put("timestamp", timestamp)
            jsonObject.put("date_time_stamp", dateTimeStamp)

            jsonObject.put(
                "true_azimuth",
                if (trueAzimuth.isNaN()) "N/A" else trueAzimuth.toDouble()
            )
            jsonObject.put("true_ra", if (trueRa.isNaN()) "N/A" else trueRa.toDouble())

            jsonObject.put("azimuth_offset", azimuthOffset.toDouble())
            jsonObject.put("pitch_offset", pitchOffset.toDouble())
            jsonObject.put("roll_offset", rollOffset.toDouble())

            jsonObject.put(
                "yaw_aka_azimuth",
                if (yawAkaAzimuth.isNaN()) "N/A" else yawAkaAzimuth.toDouble()
            )
            jsonObject.put("pitch", if (pitch.isNaN()) "N/A" else pitch.toDouble())
            jsonObject.put("roll", if (roll.isNaN()) "N/A" else roll.toDouble())

            jsonObject.toString(4)
        } catch (e: Exception) {
            """
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

            val target = jsonObject.optString("target", "Moon")
            val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
            val dateTime = jsonObject.optString("date_time_stamp", "N/A")

            val azOffset = jsonObject.optDouble("azimuth_offset", 0.0).toFloat()
            val ptOffset = jsonObject.optDouble("pitch_offset", 0.0).toFloat()
            val rlOffset = jsonObject.optDouble("roll_offset", 0.0).toFloat()

            val trueAz = parseOptionalFloat(jsonObject, "true_azimuth")
            val trueRa = parseOptionalFloat(jsonObject, "true_ra")
            val yawAka = parseOptionalFloat(jsonObject, "yaw_aka_azimuth")
            val pVal = parseOptionalFloat(jsonObject, "pitch")
            val rVal = parseOptionalFloat(jsonObject, "roll")

            MoonCalibrationData(
                timestamp = timestamp,
                azimuthOffset = azOffset,
                pitchOffset = ptOffset,
                rollOffset = rlOffset,
                targetCelestialBody = target,
                dateTimeStamp = dateTime,
                trueAzimuth = if (trueAz.isNaN()) azOffset else trueAz,
                trueRa = trueRa,
                yawAkaAzimuth = if (yawAka.isNaN()) azOffset else yawAka,
                pitch = if (pVal.isNaN()) ptOffset else pVal,
                roll = if (rVal.isNaN()) rlOffset else rVal
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure reading or decoding calibration JSON payload", e)
            null
        }
    }

    private fun parseOptionalFloat(jsonObject: JSONObject, key: String): Float {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) return Float.NaN
        val valueStr = jsonObject.optString(key)
        if (valueStr == "N/A") return Float.NaN
        return jsonObject.optDouble(key, Double.NaN).toFloat()
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
