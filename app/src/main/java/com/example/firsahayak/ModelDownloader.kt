package com.example.firsahayak

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles Gemma model file location for two modes:
 *
 * ── MODE 1: Development (ADB push) ──────────────────────────────────────────
 *   Push model to phone once during development:
 *     adb shell mkdir -p /data/local/tmp/llm/
 *     adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/
 *   App detects it at ADB_PATH automatically. No download needed.
 *
 * ── MODE 2: Production (runtime download) ───────────────────────────────────
 *   Model downloads from DOWNLOAD_URL to app private storage on first launch.
 *   Replace DOWNLOAD_URL with your own CDN or a HuggingFace direct link.
 *
 * getModelPath() returns:
 *   - The ADB path if that file exists       (dev mode)
 *   - The app storage path if downloaded     (production)
 *   - null if model is not found anywhere    (triggers NeedDownload state)
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    // ── Model filename — must match exactly what you pushed / downloaded ──────
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

    // ── ADB development path ──────────────────────────────────────────────────
    private const val ADB_PATH = "/data/local/tmp/llm/$MODEL_FILENAME"

    // ── Production download URL ───────────────────────────────────────────────
    // Replace this with your actual URL.
    // For HuggingFace gated models you may need:
    //   connection.setRequestProperty("Authorization", "Bearer YOUR_HF_TOKEN")
    private const val DOWNLOAD_URL ="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    // ── App private storage path (used for downloaded model) ─────────────────
    private fun appStoragePath(context: Context): String =
        File(context.filesDir, MODEL_FILENAME).absolutePath

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the absolute path to the model file if it exists, null otherwise.
     * Checks ADB dev path first, then app private storage.
     *
     * Called by FirViewModel.checkAndInitialize() — if null is returned,
     * the ViewModel switches to NeedDownload state.
     */
    fun getModelPath(context: Context): String? {
        // Check 1 — ADB pushed path (development)
        if (File(ADB_PATH).exists()) {
            Log.i(TAG, "Model found at ADB path: $ADB_PATH")
            return ADB_PATH
        }
        // Check 2 — App private storage (production download)
        val appPath = appStoragePath(context)
        if (File(appPath).exists()) {
            Log.i(TAG, "Model found at app storage: $appPath")
            return appPath
        }
        // Not found anywhere
        Log.w(TAG, "Model not found. Push via ADB or trigger download.")
        return null
    }

    /**
     * Returns true if the model is available on this device.
     * Convenience wrapper around getModelPath().
     */
    fun isModelDownloaded(context: Context): Boolean =
        getModelPath(context) != null

    /**
     * Returns a human-readable description of where the model was found.
     * Useful for debug UI or settings screens.
     */
    fun getModelSource(context: Context): String = when {
        File(ADB_PATH).exists()                -> "ADB dev path"
        File(appStoragePath(context)).exists() -> "App storage (downloaded)"
        else                                   -> "Not found"
    }

    fun deleteTempFile(context: Context) {
        val temp = File(context.filesDir, "$MODEL_FILENAME.tmp")
        if (temp.exists()) {
            temp.delete()
            Log.i(TAG, "Deleted corrupt temp file — will restart from 0")
        }
    }

    /**
     * Download the model from DOWNLOAD_URL to app private storage.
     * Restarts the downloading when partial downloaded if interrupted.
     * Reports download progress via onProgress(0..100).
     *
     * This is a suspend function — call it from a coroutine (viewModelScope).
     * It runs on Dispatchers.IO so it won't block the UI thread.
     */
    suspend fun downloadModel(
        context   : Context,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {

        val finalFile = File(appStoragePath(context))
        val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")

        // If temp file is smaller it's likely a corrupt stub —
        // delete it and start fresh rather than resuming from bad bytes
        
        if (tempFile.exists() && tempFile.length() < 1_048_576L) {
            Log.w(TAG, "Temp file too small (${tempFile.length()} bytes) — deleting, starting fresh")
            tempFile.delete()
        }

        // Resume support — check how many bytes we already have
        val resumeFrom: Long = if (tempFile.exists()) tempFile.length() else 0L
        Log.i(TAG, "Starting download. Resume from byte: $resumeFrom")

        val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000    // 15s to connect
            readTimeout = 60_000    // 60s between data packets
            if (resumeFrom > 0) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
        }

        val responseCode = connection.responseCode

        // 200 = full download, 206 = resumed partial download
        if (responseCode != HttpURLConnection.HTTP_OK &&
            responseCode != HttpURLConnection.HTTP_PARTIAL
        ) {
            throw RuntimeException("Server returned HTTP $responseCode for model download.")
        }

        val isResume = (responseCode == HttpURLConnection.HTTP_PARTIAL)
        val totalBytes = if (isResume) {
            resumeFrom + connection.contentLengthLong
        } else {
            if (!isResume && tempFile.exists()) tempFile.delete()
            connection.contentLengthLong
        }

        var downloaded = if (isResume) resumeFrom else 0L
        var lastPct = -1

        Log.i(TAG, "Total size: ${totalBytes / 1_000_000} MB | Resuming: $isResume")

        // Stream download → temp file (append if resuming)
        connection.inputStream.use { input ->
            tempFile.outputStream().also { stream ->
                // If resuming, seek to end of existing data
                if (isResume) stream.channel.position(resumeFrom)
            }.use { output ->
                val buffer = ByteArray(65_536)   // 64 KB read buffer
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    // Report progress only when percentage changes
                    val pct = if (totalBytes > 0) ((downloaded * 100L) / totalBytes).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct)
                    }
                }
            }
        }

        // Rename temp → final file atomically
        if (!tempFile.renameTo(finalFile)) {
            // renameTo can fail across filesystems — fallback to copy+delete
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        onProgress(100)
        Log.i(TAG, "Download complete: ${finalFile.absolutePath}")
    }

    /**
     * Delete the downloaded model from app private storage.
     * Frees ~1.5 GB. Does NOT delete the ADB-pushed version.
     * Use in a settings screen "Clear model / Free storage" option.
     */
    fun deleteDownloadedModel(context: Context) {
        val model = File(appStoragePath(context))
        val temp  = File(context.filesDir, "$MODEL_FILENAME.tmp")
        if (model.exists()) { model.delete(); Log.i(TAG, "Deleted model file.") }
        if (temp.exists())  { temp.delete();  Log.i(TAG, "Deleted temp file.") }
    }
}
