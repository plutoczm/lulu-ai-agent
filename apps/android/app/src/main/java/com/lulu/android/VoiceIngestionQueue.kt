package com.lulu.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class VoiceIngestionJob(
    val id: Long,
    val accountId: String,
    val platform: String,
    val notificationSourceKey: String,
    val observedAtMillis: Long,
    val audioUri: String,
    val audioMimeType: String,
    val attempts: Int,
    val nextRetryAtMillis: Long,
    val lastError: String
)

data class VoiceRetryDecision(
    val retryAtMillis: Long?,
    val exhausted: Boolean
)

object VoiceIngestionQueue {
    private const val DB_NAME = "lulu_voice_ingestion.db"
    private const val DB_VERSION = 3
    private const val TABLE = "voice_ingestion_job"
    private const val CLAIM_TABLE = "voice_source_claim"
    private const val MAX_ATTEMPTS = 8
    private const val CLAIM_RETENTION_MS = 14L * 24L * 60L * 60L * 1000L

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    notification_source_key TEXT NOT NULL,
                    observed_at_ms INTEGER NOT NULL,
                    audio_uri TEXT NOT NULL DEFAULT '',
                    audio_mime_type TEXT NOT NULL DEFAULT '',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    next_retry_at_ms INTEGER NOT NULL DEFAULT 0,
                    state TEXT NOT NULL DEFAULT 'pending',
                    last_error TEXT NOT NULL DEFAULT '',
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            createIndexesAndClaims(db)
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            if (oldVersion < 2) {
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN next_retry_at_ms INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN state TEXT NOT NULL DEFAULT 'pending'"
                )
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN last_error TEXT NOT NULL DEFAULT ''"
                )
            }
            if (oldVersion < 3) {
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN audio_uri TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE $TABLE " +
                        "ADD COLUMN audio_mime_type TEXT NOT NULL DEFAULT ''"
                )
            }
            createIndexesAndClaims(db)
        }

        private fun createIndexesAndClaims(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS ux_voice_job_source
                ON $TABLE(account_id, notification_source_key)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS ix_voice_job_retry
                ON $TABLE(state, next_retry_at_ms, id)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $CLAIM_TABLE (
                    source_path TEXT PRIMARY KEY,
                    notification_source_key TEXT NOT NULL,
                    claimed_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Synchronized
    fun enqueue(
        context: Context,
        accountId: String,
        platform: String,
        notificationSourceKey: String,
        observedAtMillis: Long,
        audioUri: String = "",
        audioMimeType: String = ""
    ) {
        if (accountId.isBlank() || notificationSourceKey.isBlank()) return

        val now = System.currentTimeMillis()
        val db = Helper(context.applicationContext).writableDatabase
        val values = ContentValues().apply {
            put("account_id", accountId)
            put("platform", platform)
            put("notification_source_key", notificationSourceKey)
            put("observed_at_ms", observedAtMillis)
            put("audio_uri", audioUri.trim())
            put("audio_mime_type", audioMimeType.trim())
            put("next_retry_at_ms", now)
            put("state", "pending")
            put("created_at_ms", now)
        }
        db.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        if (audioUri.isNotBlank()) {
            val attachment = ContentValues().apply {
                put("audio_uri", audioUri.trim())
                put("audio_mime_type", audioMimeType.trim())
                put("state", "pending")
                put("next_retry_at_ms", now)
                put("last_error", "")
            }
            db.update(
                TABLE,
                attachment,
                "account_id = ? AND notification_source_key = ?",
                arrayOf(accountId, notificationSourceKey)
            )
        }
        db.close()
    }

    @Synchronized
    fun pending(
        context: Context,
        limit: Int = 12,
        nowMillis: Long = System.currentTimeMillis()
    ): List<VoiceIngestionJob> {
        val db = Helper(context.applicationContext).readableDatabase
        val rows = mutableListOf<VoiceIngestionJob>()
        val cursor = db.query(
            TABLE,
            arrayOf(
                "id",
                "account_id",
                "platform",
                "notification_source_key",
                "observed_at_ms",
                "audio_uri",
                "audio_mime_type",
                "attempts",
                "next_retry_at_ms",
                "last_error"
            ),
            "state IN (?, ?) AND attempts < ? AND next_retry_at_ms <= ?",
            arrayOf(
                "pending",
                "waiting_audio",
                MAX_ATTEMPTS.toString(),
                nowMillis.toString()
            ),
            null,
            null,
            "next_retry_at_ms ASC, id ASC",
            limit.coerceIn(1, 50).toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                rows += VoiceIngestionJob(
                    id = it.getLong(0),
                    accountId = it.getString(1),
                    platform = it.getString(2),
                    notificationSourceKey = it.getString(3),
                    observedAtMillis = it.getLong(4),
                    audioUri = it.getString(5).orEmpty(),
                    audioMimeType = it.getString(6).orEmpty(),
                    attempts = it.getInt(7),
                    nextRetryAtMillis = it.getLong(8),
                    lastError = it.getString(9).orEmpty()
                )
            }
        }
        db.close()
        return rows
    }

    @Synchronized
    fun markAttempt(
        context: Context,
        id: Long,
        reason: String
    ): VoiceRetryDecision {
        val now = System.currentTimeMillis()
        val db = Helper(context.applicationContext).writableDatabase
        val currentAttempts = db.query(
            TABLE,
            arrayOf("attempts"),
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use {
            if (it.moveToFirst()) it.getInt(0) else null
        }

        if (currentAttempts == null) {
            db.close()
            return VoiceRetryDecision(null, exhausted = true)
        }

        val nextAttempts = currentAttempts + 1
        val exhausted = nextAttempts >= MAX_ATTEMPTS
        val delay = retryDelayMillis(nextAttempts)
        val retryAt = if (exhausted) null else now + delay

        val values = ContentValues().apply {
            put("attempts", nextAttempts)
            put("last_error", reason.trim().take(400))
            put("state", if (exhausted) "failed" else "pending")
            put(
                "next_retry_at_ms",
                retryAt ?: Long.MAX_VALUE
            )
        }
        db.update(
            TABLE,
            values,
            "id = ?",
            arrayOf(id.toString())
        )
        db.close()
        return VoiceRetryDecision(retryAt, exhausted)
    }

    @Synchronized
    fun markWaitingAudio(
        context: Context,
        id: Long,
        reason: String
    ) {
        val db = Helper(context.applicationContext).writableDatabase
        val values = ContentValues().apply {
            put("state", "waiting_audio")
            // waiting_audio is event-driven: a new notification attachment,
            // app/service reconnect, or explicit flush retries the resolver.
            // Do not wake the app on a fixed polling loop.
            put("next_retry_at_ms", 0L)
            put("last_error", reason.trim().take(400))
        }
        db.update(
            TABLE,
            values,
            "id = ?",
            arrayOf(id.toString())
        )
        db.close()
    }

    @Synchronized
    fun markFailed(
        context: Context,
        id: Long,
        reason: String
    ) {
        val db = Helper(context.applicationContext).writableDatabase
        val values = ContentValues().apply {
            put("attempts", MAX_ATTEMPTS)
            put("last_error", reason.trim().take(400))
            put("state", "failed")
            put("next_retry_at_ms", Long.MAX_VALUE)
        }
        db.update(
            TABLE,
            values,
            "id = ?",
            arrayOf(id.toString())
        )
        db.close()
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        val db = Helper(context.applicationContext).writableDatabase
        db.delete(TABLE, "id = ?", arrayOf(id.toString()))
        db.close()
    }

    @Synchronized
    fun claimSource(
        context: Context,
        sourcePath: String,
        notificationSourceKey: String
    ) {
        if (sourcePath.isBlank()) return
        val db = Helper(context.applicationContext).writableDatabase
        pruneClaims(db)
        val values = ContentValues().apply {
            put("source_path", sourcePath)
            put("notification_source_key", notificationSourceKey)
            put("claimed_at_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            CLAIM_TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        db.close()
    }

    @Synchronized
    fun claimedSourcePaths(context: Context): Set<String> {
        val db = Helper(context.applicationContext).writableDatabase
        pruneClaims(db)
        val paths = mutableSetOf<String>()
        db.query(
            CLAIM_TABLE,
            arrayOf("source_path"),
            null,
            null,
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                paths += it.getString(0)
            }
        }
        db.close()
        return paths
    }

    @Synchronized
    fun nextRetryAtMillis(context: Context): Long? {
        val db = Helper(context.applicationContext).readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT MIN(next_retry_at_ms)
            FROM $TABLE
            WHERE state = 'pending'
              AND attempts < ?
            """.trimIndent(),
            arrayOf(MAX_ATTEMPTS.toString())
        )
        val value = cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                it.getLong(0)
            } else {
                null
            }
        }
        db.close()
        return value
    }

    @Synchronized
    fun count(context: Context): Int =
        countByStates(context, listOf("pending", "waiting_audio"))

    @Synchronized
    fun failedCount(context: Context): Int =
        countByState(context, "failed")

    @Synchronized
    fun retryFailed(context: Context): Int {
        val db = Helper(context.applicationContext).writableDatabase
        val values = ContentValues().apply {
            put("attempts", 0)
            put("next_retry_at_ms", System.currentTimeMillis())
            put("state", "pending")
            put("last_error", "")
        }
        val rows = db.update(
            TABLE,
            values,
            "state = ?",
            arrayOf("failed")
        )
        db.close()
        return rows
    }

    private fun countByState(
        context: Context,
        state: String
    ): Int = countByStates(context, listOf(state))

    private fun countByStates(
        context: Context,
        states: List<String>
    ): Int {
        if (states.isEmpty()) return 0
        val db = Helper(context.applicationContext).readableDatabase
        val placeholders = states.joinToString(",") { "?" }
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE " +
                "WHERE state IN ($placeholders)",
            states.toTypedArray()
        )
        val count = cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        db.close()
        return count
    }

    private fun pruneClaims(db: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - CLAIM_RETENTION_MS
        db.delete(
            CLAIM_TABLE,
            "claimed_at_ms < ?",
            arrayOf(cutoff.toString())
        )
    }

    private fun retryDelayMillis(attempt: Int): Long =
        when (attempt) {
            1 -> 3_000L
            2 -> 8_000L
            3 -> 20_000L
            4 -> 45_000L
            5 -> 90_000L
            6 -> 180_000L
            else -> 300_000L
        }
}
