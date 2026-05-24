package com.ryan.smalltalk.llm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import java.io.File

enum class ResponderVariant(
    val label: String,
    val description: String,
    val filename: String,
) {
    E2B("Gemma 4 E2B", "Recommended · fast, handles web, chat & images", "gemma-4-E2B-it.litertlm"),
    E4B("Gemma 4 E4B", "Heavier · occasionally deeper, slower", "gemma-4-E4B-it.litertlm"),
    // Gemma 3 8B: for high-RAM devices (12 GB+). Hidden unless the file exists alongside the others.
    E8B("Gemma 3 8B", "Largest · best reasoning, needs 12 GB+ RAM", "gemma-3-8b-it.litertlm"),
}

/**
 * Tracks where the two on-device model files live and how to reach them.
 *
 * Access strategy (chosen for v1): all-files access + a real filesystem path. LiteRT-LM's
 * [com.google.ai.edge.litertlm.EngineConfig.modelPath] requires an absolute path, not a
 * content:// URI, so we resolve the SAF pick down to `/storage/emulated/0/...` and persist the
 * resolved path. The ~3 GB of weights are never copied.
 */
object ModelFiles {

    private const val PREFS = "smalltalk_models"
    private const val KEY_ROUTER = "functiongemma_path"
    private const val KEY_RESPONDER = "gemma_e2b_path"
    private const val KEY_RESPONDER_VARIANT = "responder_variant"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRouterPath(context: Context): String? = prefs(context).getString(KEY_ROUTER, null)

    /**
     * Returns the path for the active [ResponderVariant] if the file is readable, otherwise falls
     * back to the raw stored path (so existing setups without the E4B file still work).
     */
    fun getResponderPath(context: Context): String? {
        val variant = getResponderVariant(context)
        val base = prefs(context).getString(KEY_RESPONDER, null) ?: return null
        val variantPath = siblingPath(base, variant)
        return if (isReadable(variantPath)) variantPath else base
    }

    fun setRouterPath(context: Context, path: String) =
        prefs(context).edit().putString(KEY_ROUTER, path).apply()

    fun setResponderPath(context: Context, path: String) =
        prefs(context).edit().putString(KEY_RESPONDER, path).apply()

    // ---- Responder variant (E2B / E4B picker) ----

    fun getResponderVariant(context: Context): ResponderVariant {
        val name = prefs(context).getString(KEY_RESPONDER_VARIANT, null) ?: return ResponderVariant.E2B
        return ResponderVariant.entries.firstOrNull { it.name == name } ?: ResponderVariant.E2B
    }

    fun setResponderVariant(context: Context, variant: ResponderVariant) =
        prefs(context).edit().putString(KEY_RESPONDER_VARIANT, variant.name).apply()

    /**
     * Returns the path for [variant] computed from the same directory as the stored responder
     * path, or null if that file isn't readable (so the picker can disable unavailable options).
     */
    fun getVariantPathIfReadable(context: Context, variant: ResponderVariant): String? {
        val base = prefs(context).getString(KEY_RESPONDER, null) ?: return null
        val path = siblingPath(base, variant)
        return if (isReadable(path)) path else null
    }

    private fun siblingPath(basePath: String, variant: ResponderVariant): String {
        val dir = File(basePath).parent ?: return basePath
        return "$dir/${variant.filename}"
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    /** The responder is configured and readable. Router is optional — works without it. */
    fun isConfigured(context: Context): Boolean {
        val g = getResponderPath(context)
        return hasAllFilesAccess() && g != null && isReadable(g)
    }

    fun isReadable(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val f = File(path)
        return f.exists() && f.canRead() && f.length() > 0L
    }

    // ---- All-files access (MANAGE_EXTERNAL_STORAGE) ----

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun allFilesAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri(),
        )

    /**
     * Best-effort resolution of an ACTION_OPEN_DOCUMENT result to an absolute path.
     * Handles the common external-storage provider; returns null if it cannot be mapped
     * (caller should then fall back to the manual path field).
     */
    fun resolveDocumentUriToPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = try {
            android.provider.DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            return null
        }
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val (volume, relative) = parts
        return if (volume.equals("primary", ignoreCase = true)) {
            "${Environment.getExternalStorageDirectory().absolutePath}/$relative"
        } else {
            // Removable / secondary volume.
            "/storage/$volume/$relative"
        }
    }
}
