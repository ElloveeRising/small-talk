package com.ryan.smalltalk.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.net.toUri
import com.ryan.smalltalk.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raw SQLite conversation history (no Room, per spec). Local-only, never synced or exported.
 * A single rolling thread is persisted; "Clear All" wipes the table completely.
 */
class HistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                image_uri TEXT,
                ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    suspend fun append(message: Message) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("role", message.role)
            put("text", message.text)
            put("image_uri", message.imageUri?.toString())
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        Unit
    }

    suspend fun loadAll(): List<Message> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Message>()
        readableDatabase.query(
            TABLE, null, null, null, null, null, "ts ASC"
        ).use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val roleCol = c.getColumnIndexOrThrow("role")
            val textCol = c.getColumnIndexOrThrow("text")
            val imgCol = c.getColumnIndexOrThrow("image_uri")
            while (c.moveToNext()) {
                val rawUri = c.getString(imgCol)
                out += Message(
                    id = c.getString(idCol),
                    role = c.getString(roleCol),
                    text = c.getString(textCol),
                    imageUri = rawUri?.let { runCatching { it.toUri() }.getOrNull() },
                )
            }
        }
        out
    }

    /** One-tap wipe: empties the conversation table completely. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE, null, null)
        Unit
    }

    /**
     * Synchronous wipe — only call this from a place where blocking the calling thread
     * for a few ms is fine (e.g. Application.onCreate, where we need the table empty
     * BEFORE the ChatViewModel kicks off its async loadAll on a different thread).
     */
    fun clearAllSync() {
        writableDatabase.delete(TABLE, null, null)
    }

    companion object {
        private const val DB_NAME = "smalltalk_history.db"
        private const val DB_VERSION = 1
        private const val TABLE = "messages"
    }
}
