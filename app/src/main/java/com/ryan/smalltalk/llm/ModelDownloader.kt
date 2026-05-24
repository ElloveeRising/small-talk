package com.ryan.smalltalk.llm

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads .litertlm model files directly from a URL into a public Downloads
 * subfolder ([MODELS_DIR_PUBLIC]) — chosen so the user can also see the file
 * in any file manager once it lands, and so [ModelFiles] (which talks to
 * LiteRT-LM by absolute path) can read it via MANAGE_EXTERNAL_STORAGE.
 *
 * Streams the response body in chunks and publishes progress as a percentage
 * for the UI. Atomic via a `.part` file that gets renamed only on success.
 */
class ModelDownloader {

    sealed interface State {
        data object Idle : State
        data class Downloading(
            val bytesSoFar: Long,
            val totalBytes: Long,
            val bytesPerSec: Long,
        ) : State {
            val pct: Float get() =
                if (totalBytes > 0) (bytesSoFar.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        }
        data class Done(val absolutePath: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun reset() { _state.value = State.Idle }

    /**
     * Downloads [url] into [MODELS_DIR_PUBLIC]/<filename>. If the destination
     * already exists with a positive size, [State.Done] is published immediately
     * and no network call happens.
     *
     * Returns the absolute path on success, or null on failure (also reflected in [state]).
     */
    suspend fun download(
        context: Context,
        url: String,
        filename: String,
    ): String? = withContext(Dispatchers.IO) {
        val dir = ensureModelsDir()
        val finalFile = File(dir, filename)
        if (finalFile.exists() && finalFile.length() > 0) {
            Log.i(TAG, "Model already present: ${finalFile.absolutePath}")
            _state.value = State.Done(finalFile.absolutePath)
            return@withContext finalFile.absolutePath
        }

        val partFile = File(dir, "$filename.part")
        // If a previous attempt left a partial file, blow it away — resumption
        // isn't worth the complexity here.
        if (partFile.exists()) partFile.delete()

        _state.value = State.Downloading(0L, 0L, 0L)

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SmallTalk/2.0 (Android)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = "HTTP ${resp.code} — ${resp.message}"
                    Log.e(TAG, "Download failed: $msg")
                    _state.value = State.Failed(msg)
                    return@withContext null
                }
                val body = resp.body ?: run {
                    _state.value = State.Failed("Empty response body")
                    return@withContext null
                }
                val total = body.contentLength().takeIf { it > 0 } ?: -1L

                body.byteStream().use { input ->
                    partFile.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = input.read(buf)
                        var soFar = 0L
                        var lastReportNs = System.nanoTime()
                        var lastReportBytes = 0L
                        while (read > 0) {
                            if (!coroutineContext.isActive) {
                                partFile.delete()
                                _state.value = State.Idle
                                return@withContext null
                            }
                            out.write(buf, 0, read)
                            soFar += read
                            val now = System.nanoTime()
                            if (now - lastReportNs > 200_000_000L) {  // ~5 Hz
                                val elapsedSec = (now - lastReportNs) / 1_000_000_000.0
                                val bps = if (elapsedSec > 0)
                                    ((soFar - lastReportBytes) / elapsedSec).toLong()
                                else 0L
                                _state.value = State.Downloading(soFar, total, bps)
                                lastReportNs = now
                                lastReportBytes = soFar
                            }
                            read = input.read(buf)
                        }
                    }
                }
            }

            if (!partFile.renameTo(finalFile)) {
                // Fall back to copy + delete if rename fails (e.g. across filesystems)
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            _state.value = State.Done(finalFile.absolutePath)
            finalFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)
            partFile.delete()
            _state.value = State.Failed(e.message ?: "Download failed")
            null
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"

        /** Folder where downloaded .litertlm files live. Public so a Files app can see them. */
        const val MODELS_DIR_PUBLIC = "SmallTalkModels"

        fun ensureModelsDir(): File {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val dir = File(downloads, MODELS_DIR_PUBLIC)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun expectedPathFor(filename: String): String =
            File(ensureModelsDir(), filename).absolutePath

        /**
         * Direct download URLs for the .litertlm weights, served by the
         * `litert-community` org on Hugging Face. These repos are public — no
         * account or token needed — and the files are byte-for-byte the same
         * ones Edge Gallery pulls down.
         *
         * Resolve URL format: huggingface.co/<repo>/resolve/main/<file>
         * For redundancy / if HF rate-limits, swap these for Ali's mirror.
         */
        const val E2B_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val E4B_URL =
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    }
}
