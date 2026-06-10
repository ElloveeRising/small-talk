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
 * Access strategy (v1.1): models download into the app-private external dir ([appModelsDir]) —
 * a real filesystem path LiteRT-LM can open with NO permission needed. The pre-1.1 public
 * folder ([legacyModelsDir]) and user-picked custom paths still work as fallbacks; only those
 * need MANAGE_EXTERNAL_STORAGE, which the setup wizard now requests solely on the advanced
 * "use my own file" path. LiteRT-LM requires an absolute path (not a content:// URI), and the
 * ~3 GB of weights are never copied.
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
     * Returns a readable path for the active [ResponderVariant] — app-private dir first, then
     * the legacy public dir, then the user's custom path — or null if nothing is readable.
     */
    fun getResponderPath(context: Context): String? {
        val variant = getResponderVariant(context)
        resolveVariantPath(context, variant)?.let { return it }
        // Custom file with a nonstandard name (advanced path) — honor it for the active variant.
        val base = prefs(context).getString(KEY_RESPONDER, null)
        return if (isReadable(base)) base else null
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
     * Returns a readable path for [variant], or null (so the picker can show download options).
     */
    fun getVariantPathIfReadable(context: Context, variant: ResponderVariant): String? =
        resolveVariantPath(context, variant)

    // ---- Model storage locations ----

    /**
     * App-private external dir for downloaded models: needs no permission, gives LiteRT-LM a
     * real filesystem path, and is cleaned up automatically on uninstall. The v1.1+ default.
     */
    fun appModelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "models").apply { if (!exists()) mkdirs() }

    /**
     * Pre-1.1 public download location (Downloads/SmallTalkModels). Reading it requires
     * all-files access — kept as a fallback so existing installs keep working untouched.
     */
    fun legacyModelsDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SmallTalkModels",
        )

    /**
     * Resolves a readable file for [variant]: app-private dir, then the legacy public dir,
     * then a sibling of the user's stored custom path. Null if none are readable.
     */
    fun resolveVariantPath(context: Context, variant: ResponderVariant): String? {
        val appFile = File(appModelsDir(context), variant.filename).absolutePath
        if (isReadable(appFile)) return appFile
        val legacyFile = File(legacyModelsDir(), variant.filename).absolutePath
        if (isReadable(legacyFile)) return legacyFile
        val base = prefs(context).getString(KEY_RESPONDER, null) ?: return null
        val sibling = siblingPath(base, variant)
        return if (isReadable(sibling)) sibling else null
    }

    private fun siblingPath(basePath: String, variant: ResponderVariant): String {
        val dir = File(basePath).parent ?: return basePath
        return "$dir/${variant.filename}"
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    /**
     * The responder is configured and readable. Router is optional — works without it.
     * All-files access is deliberately NOT required here: the standard app-private download
     * needs no permission at all. Only the advanced bring-your-own-file path requests it.
     */
    fun isConfigured(context: Context): Boolean = getResponderPath(context) != null

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
