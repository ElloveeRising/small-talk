package com.ryan.smalltalk

import android.app.Application
import android.content.Context
import com.ryan.smalltalk.data.HistoryStore
import com.ryan.smalltalk.llm.ModelDownloader
import com.ryan.smalltalk.llm.ModelManager
import com.ryan.smalltalk.pipeline.ChatPipeline
import com.ryan.smalltalk.tools.SmallTalkToolSet
import com.ryan.smalltalk.tools.ToolExecutor

class SmallTalkApp : Application() {

    val history: HistoryStore by lazy { HistoryStore(this) }
    val models: ModelManager by lazy { ModelManager() }
    val downloader: ModelDownloader by lazy { ModelDownloader() }
    val toolExecutor: ToolExecutor by lazy { ToolExecutor(webAugmentationEnabled) }
    val toolBridge: SmallTalkToolSet by lazy { SmallTalkToolSet(toolExecutor) }
    val pipeline: ChatPipeline by lazy { ChatPipeline(models, toolExecutor, toolBridge) }

    override fun onCreate() {
        super.onCreate()
        // Privacy by default: every cold process start gets a fresh conversation.
        // Backgrounding the app preserves state; killing it (or a reboot) wipes.
        // SYNCHRONOUS on purpose — ChatViewModel.init kicks off its own history
        // loadAll() in a coroutine moments from now, and we need the table empty
        // before that read happens, otherwise the user briefly sees stale messages.
        // A SQLite delete on a tiny table is sub-millisecond; safe on the main thread.
        runCatching { history.clearAllSync() }
    }

    private val prefs by lazy { getSharedPreferences("smalltalk_settings", Context.MODE_PRIVATE) }

    var webAugmentationEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB, true)
        set(value) {
            prefs.edit().putBoolean(KEY_WEB, value).apply()
            toolExecutor.webAugmentationEnabled = value
        }

    /** Gemma 4 "thinking" mode. Default OFF — it's slower on-device; opt-in for harder asks. */
    var thinkingEnabled: Boolean
        get() = prefs.getBoolean(KEY_THINKING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_THINKING, value).apply()
        }

    companion object {
        private const val KEY_WEB = "web_augmentation"
        private const val KEY_THINKING = "thinking_mode"
    }
}
