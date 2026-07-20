// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CalibrationStorageManager.kt
package com.example.helloworldkotlinandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

data class CalibrationData(
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
        private const val FILE_NAME = "calibration_data_newest.json"
        private const val ALTERNATE_FILE_NAME = "moon_sensor_calibration.json"
    }

    private fun appendToExternalLog(payload: String, operationNotice: String) {
        try {
            var dateStr: String? = null
            val pseudoJsonParts = mutableListOf<String>()

            try {
                val jsonObject = JSONObject(payload)

                if (jsonObject.has("date_time_stamp")) {
                    val dt = jsonObject.optString("date_time_stamp")
                    if (dt.isNotBlank() && dt != "N/A") {
                        dateStr = dt
                    }
                }
                if (dateStr == null && jsonObject.has("timestamp")) {
                    val ts = jsonObject.optLong("timestamp", 0L)
                    if (ts > 0) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        dateStr = sdf.format(Date(ts))
                    }
                }

                // 1. Filtered attribute: calibration_type
                val calType = jsonObject.optString(
                    "calibration_type",
                    jsonObject.optString("target", "")
                )
                if (calType.isNotBlank() && calType != "N/A") {
                    pseudoJsonParts.add("\"calibration_type\":\"$calType\"")
                }

                // Helper for numeric degree fields rounded to 2 digits
                fun addDegreeField(key: String) {
                    if (jsonObject.has(key) && !jsonObject.isNull(key)) {
                        val value = jsonObject.optDouble(key, Double.NaN)
                        if (!value.isNaN()) {
                            val formattedVal = String.format(Locale.US, "%.2f", value)
                            pseudoJsonParts.add("\"$key\":$formattedVal°")
                        }
                    }
                }

                // 2. Filtered numeric degree attributes present in example
                addDegreeField("true_azimuth")
                addDegreeField("true_ra")
                addDegreeField("azimuth_offset")
                addDegreeField("pitch_offset")
                addDegreeField("roll_offset")
            } catch (e: Exception) {
                // If payload is empty or not valid JSON, pseudo-JSON array remains empty
            }

            if (dateStr == null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                dateStr = sdf.format(Date())
            }

            // Determine log level and sanitize notice sentence
            var logLevel = "[INFO]"
            var cleanNotice = operationNotice.trim()

            if (cleanNotice.startsWith("ERROR:", ignoreCase = true)) {
                logLevel = "[ERROR]"
                cleanNotice = cleanNotice.substring("ERROR:".length).trim()
            } else if (cleanNotice.startsWith("NOTICE:", ignoreCase = true)) {
                logLevel = "[NOTICE]"
                cleanNotice = cleanNotice.substring("NOTICE:".length).trim()
            }

            cleanNotice = cleanNotice
                .replace("from file ", "from ")
                .replace("to file ", "to ")
            if (cleanNotice.isNotEmpty()) {
                cleanNotice = cleanNotice.replaceFirstChar { it.uppercase() }
            }

            val logLineBuilder = StringBuilder()
            logLineBuilder.append(
                dateStr
            ).append(" ").append(logLevel).append(" ").append(cleanNotice)

            if (pseudoJsonParts.isNotEmpty()) {
                logLineBuilder.append(" ").append(pseudoJsonParts.joinToString(" "))
            }
            logLineBuilder.append("\n")

            val logDir = File("/storage/FF9D-1400/Download/IT/current/logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "operations.log")

            FileWriter(logFile, true).use { writer ->
                writer.write(logLineBuilder.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure writing to the external operation log file", e)
        }
    }

    fun writeCalibrationToAllStorages(data: CalibrationData): Boolean {
        val payload = data.toJsonString()

        appendToExternalLog(payload, "Writing JSON data to $FILE_NAME")

        val internalSuccess = saveToInternalStorage(payload)
        val sdCardSuccess = saveToPhysicalSdCard(payload)

        if (internalSuccess && sdCardSuccess) {
            Log.d(TAG, "Calibration data mirrored safely to both filesystems.")
        }
        return internalSuccess
    }

    fun readLatestCalibration(): CalibrationData? {
        var chosenFileName = FILE_NAME
        var targetFile = File(context.filesDir, chosenFileName)

        if (!targetFile.exists()) {
            chosenFileName = ALTERNATE_FILE_NAME
            targetFile = File(context.filesDir, chosenFileName)
        }

        if (!targetFile.exists()) {
            appendToExternalLog(
                "{}",
                "NOTICE: Initial load skipped. Neither " +
                    FILE_NAME + " nor $ALTERNATE_FILE_NAME was found."
            )
            return null
        }

        return try {
            val jsonString = context.openFileInput(
                chosenFileName
            ).bufferedReader().use { it.readText() }

            appendToExternalLog(jsonString, "Loading JSON data from $chosenFileName")

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

            CalibrationData(
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
            appendToExternalLog(
                "{\"error\":\"${e.javaClass.simpleName}\",\"message\":\"${e.message}\"}",
                "ERROR: Impossibility to read or decode from $chosenFileName"
            )
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
            appendToExternalLog(
                "{}",
                "ERROR: Internal write operation failed to execute: ${e.message}"
            )
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
            appendToExternalLog(
                "{}",
                "ERROR: SD card write operation failed to execute: ${e.message}"
            )
            false
        }
    }
}
