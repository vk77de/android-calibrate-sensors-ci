// File: ./app/src/main/java/com/example/helloworldkotlinandroid/OperationsLog.kt
package com.example.helloworldkotlinandroid

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Resolves and caches the shared `operations.log` directory used by both
 * [CalibrationStorageManager] and [CelestialObjectsCalculator]'s debug logging.
 *
 * Previously this path was hardcoded with a specific SD card's volume ID
 * (`/storage/FF9D-1400/Download/IT/current/logs`), which silently broke log
 * writes on any other device, or the same device after re-inserting a
 * differently-formatted card — `mkdirs()`/`FileWriter` would just throw and
 * get swallowed by the enclosing `catch`. This resolves the directory
 * dynamically instead, trying a short list of candidates and caching
 * whichever one first proves writable.
 */
object OperationsLog {
    private const val RELATIVE_LOG_PATH = "Download/IT/current/logs"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedDir: File? = null

    /**
     * Call once (e.g. from [MainActivity.onCreate], via [CalibrationStorageManager]'s
     * constructor) so that [Context.getExternalFilesDirs] — which reliably enumerates
     * any physical SD card without needing to know its volume ID — can be used when
     * resolving the log directory.
     */
    fun configure(context: Context) {
        appContext = context.applicationContext
        cachedDir = null
    }

    /** Returns a writable log directory, creating it if necessary, or null if none was found. */
    fun resolveLogDir(): File? {
        // Return the cached path early ONLY if it was
        // previously resolved and is still valid on disk.
        // (Because we don't cache on null appContext,
        // a non-null cachedDir guarantees a valid prior resolution.)

        cachedDir?.let { if (it.exists() || it.mkdirs()) return it }

        val context = appContext ?: run {
            Log.w(
                "OperationsLog",
                "resolveLogDir called " +
                    "before OperationsLog.configure(context)!"
            )
            return null // Don't fall back to internal storage or
            // cache bad state if context is missing
        }

        val candidates = mutableListOf<File>()

        // Now safely iterate through volumes with guaranteed context...
        // 1. Any physical SD card visible through the app's external-files-dirs API.
        //    getExternalFilesDirs(null) returns .../Android/data/<pkg>/files on each
        //    mounted volume; walk up to the volume root and apply the relative path.
        context.getExternalFilesDirs(null)?.reversed()?.forEach { filesDir ->
            var root: File? = filesDir
            repeat(4) { root = root?.parentFile }
            root?.let { candidates.add(File(it, RELATIVE_LOG_PATH)) }
        }

        // 2. Fallback: scan /storage directly for any other writable mounted volume,
        //    in case getExternalFilesDirs didn't expose it.
        File("/storage").listFiles()?.forEach { volume ->
            if (volume.isDirectory && volume.name != "self" && volume.canWrite()) {
                candidates.add(File(volume, RELATIVE_LOG_PATH))
            }
        }

        // 3. Ultimate fallback: Primary shared storage
        // works once MANAGE_EXTERNAL_STORAGE is granted,
        //    and needs no volume ID since it's always the device's main storage.
        candidates.add(File(Environment.getExternalStorageDirectory(), RELATIVE_LOG_PATH))

        for (candidate in candidates) {
            try {
                if (candidate.exists() || candidate.mkdirs()) {
                    cachedDir = candidate
                    return candidate
                }
            } catch (e: Exception) {
                // Not writable / not mounted — try the next candidate.
            }
        }
        return null
    }
}
