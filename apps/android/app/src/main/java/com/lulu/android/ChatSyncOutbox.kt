package com.lulu.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingChatSync(
    val id: Long,
    val accountId: String,
    val message: ChatSyncMessage
)

/**
 * Durable phone-side outbox for chat observations.
 *
 * Notifications and passive screen captures are written here first. Backend
 * delivery can fail or be temporarily unavailable without losing messages.
 */
object ChatSyncOutbox {
    private const val DB_NAME = "lulu_chat_sync.db"
    private const val DB_VERSION = 2
    private const val TABLE = "pending_chat_sync"

    private class Helper(context: Context) :
        SQLiteOpenHelper(
            context,
            DB_NAME,
            null,
            DB_VERSION
        ) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    message_text TEXT NOT NULL,
                    message_time TEXT,
                    content_type TEXT NOT NULL,
                    source TEXT NOT NULL,
                    source_key TEXT NOT NULL,
                    replaces_source_key TEXT NOT NULL DEFAULT '',
                    observed_at TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX ux_lulu_sync_source
                ON $TABLE(account_id, source_key)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX ix_lulu_sync_created
                ON $TABLE(created_at, id)
                """.trimIndent()
            )
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            if (oldVersion < 2) {
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN replaces_source_key TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }

    @Synchronized
    fun enqueue(
        context: Context,
        accountId: String,
        messages: List<ChatSyncMessage>
    ): Int {
        if (accountId.isBlank() || messages.isEmpty()) {
            return 0
        }

        val db = Helper(context.applicationContext).writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            for (message in messages) {
                if (message.text.isBlank() ||
                    message.sourceKey.isBlank()
                ) {
                    continue
                }
                val values = ContentValues().apply {
                    put("account_id", accountId)
                    put("sender", message.sender)
                    put("message_text", message.text)
                    put("message_time", message.time)
                    put("content_type", message.contentType)
                    put("source", message.source)
                    put("source_key", message.sourceKey)
                    put("replaces_source_key", message.replacesSourceKey)
                    put("observed_at", message.observedAt)
                    put(
                        "created_at",
                        System.currentTimeMillis()
                    )
                }
                val rowId = db.insertWithOnConflict(
                    TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId >= 0L) {
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
        return inserted
    }

    @Synchronized
    fun pending(
        context: Context,
        limit: Int = 120
    ): List<PendingChatSync> {
        val bounded = limit.coerceIn(1, 500)
        val db = Helper(context.applicationContext).readableDatabase
        val result = mutableListOf<PendingChatSync>()
        val cursor = db.query(
            TABLE,
            arrayOf(
                "id",
                "account_id",
                "sender",
                "message_text",
                "message_time",
                "content_type",
                "source",
                "source_key",
                "replaces_source_key",
                "observed_at"
            ),
            null,
            null,
            null,
            null,
            "id ASC",
            bounded.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                result += PendingChatSync(
                    id = it.getLong(0),
                    accountId = it.getString(1),
                    message = ChatSyncMessage(
                        sender = it.getString(2),
                        text = it.getString(3),
                        time = it.getString(4).orEmpty(),
                        contentType = it.getString(5),
                        source = it.getString(6),
                        sourceKey = it.getString(7),
                        replacesSourceKey = it.getString(8).orEmpty(),
                        observedAt = it.getString(9)
                    )
                )
            }
        }
        db.close()
        return result
    }

    @Synchronized
    fun delete(
        context: Context,
        ids: List<Long>
    ) {
        if (ids.isEmpty()) return
        val db = Helper(context.applicationContext).writableDatabase
        val placeholders = ids.joinToString(",") { "?" }
        db.delete(
            TABLE,
            "id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray()
        )
        db.close()
    }

    @Synchronized
    fun count(context: Context): Int {
        val db = Helper(context.applicationContext).readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE",
            null
        )
        val count = cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        db.close()
        return count
    }
}
