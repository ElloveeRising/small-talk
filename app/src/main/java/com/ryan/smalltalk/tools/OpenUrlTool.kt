package com.ryan.smalltalk.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches a URL and returns readable body text, stripped of boilerplate, capped at 2000 chars. */
class OpenUrlTool(private val client: OkHttpClient) : Tool {

    override val name = "open_url"
    override val description = "Open a specific URL and read its main text content."
    override val requiresNetwork = true

    override suspend fun execute(args: Map<String, String>): String = withContext(Dispatchers.IO) {
        var url = args["url"]?.trim().orEmpty()
        if (url.isEmpty()) return@withContext "No URL was provided."
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) SmallTalk")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "Could not open the page (HTTP ${resp.code})."
                val html = resp.body?.string().orEmpty()
                val ex = WebContent.extractReadable(html, cap = 2000)
                if (ex.body.isEmpty()) "The page had no readable text content."
                else "Page: ${ex.title}\n\n${ex.body}"
            }
        } catch (e: Exception) {
            Log.w("OpenUrlTool", "fetch failed", e)
            "Could not open the page: ${e.message}"
        }
    }
}
