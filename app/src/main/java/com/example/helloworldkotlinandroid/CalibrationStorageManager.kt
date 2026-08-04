// File: ./app/src/main/java/com/example/helloworldkotlinandroid/CalibrationStorageManager.kt
package com.example.helloworldkotlinandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Result of a celestial calibration sighting.
 *
 * The calibration offset itself ([offsetQw]/[offsetQx]/[offsetQy]/[offsetQz]) is stored as
 * a unit rotation-residual quaternion (aka rotation error / attitude error) rather than as
 * separate azimuth/pitch/roll scalars, since a quaternion has no gimbal-lock singularities
 * and composes cleanly. [targetCelestialBody] records which of the three supported sighting
 * targets — "Moon", "Sun" (typically sighted just after sunset), or "Venus" — produced it.
 */
data class CalibrationData(
    val timestamp: Long,
    val offsetQw: Float,
    val offsetQx: Float,
    val offsetQy: Float,
    val offsetQz: Float,
    val targetCelestialBody: String = "Moon",
    val dateTimeStamp: String = "N/A",
    val trueAzimuth: Float = Float.NaN,
    val trueAltitude: Float = Float.NaN
) {
    /** The calibration offset as a [Quaternion], for convenient use with [CelestialCalibrator]. */
    fun offsetQuaternion(): Quaternion = Quaternion(offsetQw, offsetQx, offsetQy, offsetQz)

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
            jsonObject.put(
                "true_altitude",
                if (trueAltitude.isNaN()) "N/A" else trueAltitude.toDouble()
            )

            // Rotation-residual quaternion: the authoritative calibration offset.
            jsonObject.put("offset_qw", offsetQw.toDouble())
            jsonObject.put("offset_qx", offsetQx.toDouble())
            jsonObject.put("offset_qy", offsetQy.toDouble())
            jsonObject.put("offset_qz", offsetQz.toDouble())

            // Human-readable derived fields (informational only, not authoritative).
            val q = offsetQuaternion()
            jsonObject.put("residual_angle_deg", q.residualAngleDegrees().toDouble())
            val euler = q.toEulerDegrees()
            jsonObject.put("residual_yaw_deg", euler[0].toDouble())
            jsonObject.put("residual_pitch_deg", euler[1].toDouble())
            jsonObject.put("residual_roll_deg", euler[2].toDouble())

            jsonObject.toString(4)
        } catch (e: Exception) {
            """
            {
                "target": "$targetCelestialBody",
                "timestamp": $timestamp,
                "offset_qw": $offsetQw,
                "offset_qx": $offsetQx,
                "offset_qy": $offsetQy,
                "offset_qz": $offsetQz
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

        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    init {
        // Lets OperationsLog see this app's external-files-dirs (and thus find any
        // physical SD card) instead of relying on a hardcoded volume ID.
        OperationsLog.configure(context)
    }

    private fun appendToExternalLog(payload: String, operationNotice: String) {
        try {
            var dateStr: String? = null
            var payloadDateStr: String? = null
            val pseudoJsonParts = mutableListOf<String>()

            try {
                val jsonObject = JSONObject(payload)

                if (jsonObject.has("date_time_stamp")) {
                    val dt = jsonObject.optString("date_time_stamp")
                    if (dt.isNotBlank() && dt != "N/A") {
                        payloadDateStr = dt
                    }
                }
                if (dateStr == null && jsonObject.has("timestamp")) {
                    val ts = jsonObject.optLong("timestamp", 0L)
                    if (ts > 0) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        payloadDateStr = sdf.format(Date(ts))
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

                // Helper for plain (non-degree) numeric fields, e.g. quaternion components
                fun addNumericField(key: String, digits: Int = 4) {
                    if (jsonObject.has(key) && !jsonObject.isNull(key)) {
                        val value = jsonObject.optDouble(key, Double.NaN)
                        if (!value.isNaN()) {
                            val formattedVal = String.format(Locale.US, "%.${digits}f", value)
                            pseudoJsonParts.add("\"$key\":$formattedVal")
                        }
                    }
                }

                // 2. Filtered attributes present in example
                addDegreeField("true_azimuth")
                addDegreeField("true_altitude")
                addNumericField("offset_qw")
                addNumericField("offset_qx")
                addNumericField("offset_qy")
                addNumericField("offset_qz")
                addDegreeField("residual_angle_deg")
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

            val gitHash = BuildConfig.GIT_HASH

            val logLineBuilder = StringBuilder()
            logLineBuilder.append(dateStr)
                .append(" ")
                .append(logLevel)
                .append(" ")
                .append(gitHash)
                .append(" ")
                .append(cleanNotice)

            if (pseudoJsonParts.isNotEmpty()) {
                logLineBuilder.append(" ").append(pseudoJsonParts.joinToString(" "))
            }
            logLineBuilder.append("\n")

            val logDir = OperationsLog.resolveLogDir()
            if (logDir == null) {
                Log.e(TAG, "Could not resolve a writable operations-log directory; skipping.")
                return
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
        val candidateFileNames = listOf(FILE_NAME, ALTERNATE_FILE_NAME)
        val candidateDirs = mutableListOf<File>()

        // 1. Internal app files directory
        candidateDirs.add(context.filesDir)

        // 2. External app files directories (primary external storage & physical SD card)
        context.getExternalFilesDirs(null)?.forEach { dir ->
            if (dir != null && !candidateDirs.contains(dir)) {
                candidateDirs.add(dir)
            }
        }

        var lastExceptionMessage: String? = null

        for (fileName in candidateFileNames) {
            for (dir in candidateDirs) {
                val candidateFile = File(dir, fileName)
                if (candidateFile.exists() && candidateFile.isFile) {
                    try {
                        val jsonString = candidateFile.readText()
                        val jsonObject = JSONObject(jsonString)

                        val target = jsonObject.optString("target", "Moon")
                        val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())

                        val currentDatetimeFormatted =
                            java.time.LocalDateTime.now().format(DATE_FORMATTER)

                        val dateTime = jsonObject.optString(
                            "date_time_stamp",
                            currentDatetimeFormatted
                        )

                        val trueAz = parseOptionalFloat(jsonObject, "true_azimuth")
                        val trueAlt = parseOptionalFloat(jsonObject, "true_altitude")

                        // Current format: the calibration offset is stored directly as a
                        // rotation-residual quaternion.
                        val qw = parseOptionalFloat(jsonObject, "offset_qw")
                        val qx = parseOptionalFloat(jsonObject, "offset_qx")
                        val qy = parseOptionalFloat(jsonObject, "offset_qy")
                        val qz = parseOptionalFloat(jsonObject, "offset_qz")

                        val offsetQuaternion = if (!qw.isNaN() && !qx.isNaN() &&
                            !qy.isNaN() && !qz.isNaN()
                        ) {
                            Quaternion(qw, qx, qy, qz).normalized()
                        } else {
                            // Legacy format (pre-quaternion calibration files, e.g.
                            // ALTERNATE_FILE_NAME): translate the stored azimuth/pitch/roll
                            // offset into an equivalent quaternion.
                            val legacyAz = jsonObject.optDouble("azimuth_offset", 0.0).toFloat()
                            val legacyPt = jsonObject.optDouble("pitch_offset", 0.0).toFloat()
                            val legacyRl = jsonObject.optDouble("roll_offset", 0.0).toFloat()
                            Quaternion.fromEulerDegrees(legacyAz, legacyPt, legacyRl)
                        }

                        val data = CalibrationData(
                            timestamp = timestamp,
                            offsetQw = offsetQuaternion.w,
                            offsetQx = offsetQuaternion.x,
                            offsetQy = offsetQuaternion.y,
                            offsetQz = offsetQuaternion.z,
                            targetCelestialBody = target,
                            dateTimeStamp = dateTime,
                            trueAzimuth = trueAz,
                            trueAltitude = trueAlt
                        )

                        appendToExternalLog(jsonString, "Loading JSON data from $fileName")
                        return data
                    } catch (e: Exception) {
                        lastExceptionMessage = "${e.javaClass.simpleName}: ${e.message}"
                        Log.e(
                            TAG,
                            "Failed reading or decoding calibration JSON " +
                                "from ${candidateFile.absolutePath}",
                            e
                        )
                    }
                }
            }
        }

        // Write error entry to operation log file if JSON calibration could not be loaded
        val errorMsg = if (lastExceptionMessage != null) {
            "ERROR: Impossibility to read or decode JSON calibration data: $lastExceptionMessage"
        } else {
            "ERROR: Impossibility to load JSON calibration data; no calibration file found."
        }
        appendToExternalLog("{}", errorMsg)
        return null
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
