package com.lulu.android

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors

/**
 * Deterministic ingestion layer shared by notifications, passive screen
 * reconciliation and the explicit coach flow.
 */
object ChatSyncCoordinator {
    private const val TAG = "LuluSync"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lulu-chat-sync").apply {
            isDaemon = true
        }
    }

    fun syncNotification(
        context: Context,
        platform: String,
        chatTitle: String?,
        text: String,
        notificationKey: String,
        observedAtMillis: Long,
        audioUri: String = "",
        audioMimeType: String = ""
    ) {
        if (text.isBlank()) return
        val identity = IdentityStore.identityForChatTitle(
            context,
            platform,
            chatTitle
        )
        if (identity == null) {
            Log.d(
                TAG,
                "notification skipped: no unique account-title match"
            )
            return
        }

        val observed = Instant.ofEpochMilli(
            observedAtMillis.coerceAtLeast(1L)
        ).toString()
        val contentType = inferContentType(text)

        val sourceKey = "notification:" + notificationKey
        enqueue(
            context,
            identity,
            listOf(
                ChatSyncMessage(
                    sender = "对方",
                    text = text.trim(),
                    time = observed,
                    contentType = contentType,
                    source = "notification",
                    sourceKey = sourceKey,
                    observedAt = observed
                )
            )
        )

        if (contentType == "voice") {
            Log.i(
                TAG,
                "voice notification platform=" + platform +
                    " audioAttachment=" + audioUri.isNotBlank()
            )
            VoiceIngestionCoordinator.scheduleFromNotification(
                context = context,
                identity = identity,
                platform = platform,
                notificationSourceKey = sourceKey,
                observedAtMillis = observedAtMillis,
                audioUri = audioUri,
                audioMimeType = audioMimeType
            )
        }
    }

    fun syncVisibleCapture(
        context: Context,
        capture: VisibleChatCapture,
        source: String = "passive_screen"
    ) {
        val identity = IdentityStore.identityForChatTitle(
            context,
            capture.platform,
            capture.chatTitle
        )
        if (identity == null) {
            Log.d(
                TAG,
                "screen sync skipped: no unique account-title match"
            )
            return
        }
        syncVisibleCaptureForIdentity(
            context,
            identity,
            capture,
            source
        )
    }

    fun syncVisibleCaptureForIdentity(
        context: Context,
        identity: ActiveIdentity,
        capture: VisibleChatCapture,
        source: String = "coach_screen"
    ) {
        if (capture.messages.isEmpty()) return

        val nowMillis = System.currentTimeMillis()
        val observed = Instant.ofEpochMilli(nowMillis).toString()
        val occurrenceCounts = mutableMapOf<String, Int>()

        val messages = capture.messages.map { message ->
            val text = message.text.trim()
            val semanticKey = message.sender + "\n" + text
            val occurrence = (occurrenceCounts[semanticKey] ?: 0) + 1
            occurrenceCounts[semanticKey] = occurrence

            // v3 deliberately avoids previous-message context. OCR can miss
            // one neighboring line between captures, which made the v2 key
            // drift and duplicated otherwise identical screen observations.
            // The occurrence index still keeps two identical visible bubbles
            // distinct inside the same capture.
            val stablePart = sha256(
                identity.account.id + "\n" +
                    semanticKey + "\n" +
                    occurrence
            )

            ChatSyncMessage(
                sender = message.sender,
                text = text,
                time = observed,
                contentType = inferContentType(message.text),
                source = source + "_" + capture.source,
                sourceKey = "screen:v3:" + stablePart,
                observedAt = observed
            )
        }

        enqueue(context, identity, messages)

        messages
            .filter {
                it.sender == "对方" &&
                    it.contentType == "voice"
            }
            .forEach { voice ->
                VoiceIngestionCoordinator.scheduleFromScreen(
                    context = context,
                    identity = identity,
                    platform = capture.platform,
                    screenSourceKey = voice.sourceKey,
                    observedAtMillis = nowMillis
                )
            }
    }

    private fun enqueue(
        context: Context,
        identity: ActiveIdentity,
        messages: List<ChatSyncMessage>
    ) {
        if (messages.isEmpty()) return
        val appContext = context.applicationContext
        val queued = ChatSyncOutbox.enqueue(
            appContext,
            identity.account.id,
            messages
        )
        if (queued > 0) {
            Log.d(
                TAG,
                "queued account=${identity.account.id} rows=$queued"
            )
        }
        flushPending(appContext)
    }

    fun flushPending(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            val baseUrl = LuluPrefs.baseUrl(appContext)
            val cookie = LuluPrefs.cookie(appContext)
            if (baseUrl.isBlank() || cookie.isBlank()) {
                Log.d(
                    TAG,
                    "outbox retained: backend session unavailable"
                )
                return@execute
            }

            repeat(6) {
                val pending = ChatSyncOutbox.pending(
                    appContext,
                    limit = 120
                )
                if (pending.isEmpty()) {
                    return@execute
                }

                val grouped = pending.groupBy { it.accountId }
                var madeProgress = false

                for ((accountId, rows) in grouped) {
                    val account = IdentityStore.accounts(appContext)
                        .firstOrNull { it.id == accountId }
                        ?: continue
                    val person = IdentityStore.person(
                        appContext,
                        account.personId
                    ) ?: continue

                    val delivery = runCatching {
                        CoachApi.syncMessages(
                            baseUrl,
                            cookie,
                            person,
                            account,
                            rows.map { it.message }
                        )
                    }

                    if (delivery.isFailure) {
                        Log.w(
                            TAG,
                            "outbox delivery failed account=$accountId; " +
                                "continuing with other accounts",
                            delivery.exceptionOrNull()
                        )
                        continue
                    }

                    ChatSyncOutbox.delete(
                        appContext,
                        rows.map { it.id }
                    )
                    madeProgress = true

                    val synced = delivery.getOrThrow()
                    Log.i(
                        TAG,
                        "outbox synced account=$accountId " +
                            "inserted=${synced.inserted} " +
                            "merged=${synced.merged} " +
                            "total=${synced.totalMessages} " +
                            "pending=${ChatSyncOutbox.count(appContext)}"
                    )
                }

                if (!madeProgress) {
                    return@execute
                }
            }
        }
    }

    private fun inferContentType(text: String): String {
        val normalized = text.trim()
        return when {
            normalized.startsWith("[语音消息") &&
                normalized.contains("尚未转写") -> "voice"
            normalized.startsWith("[语音消息") -> "voice_transcript"
            normalized in setOf(
                "[语音]",
                "[语音消息]",
                "语音",
                "语音消息",
                "一条语音",
                "一条语音消息",
                "收到一条语音",
                "收到一条语音消息",
                "发来一条语音",
                "发来一条语音消息"
            ) -> "voice"
            normalized.contains("[图片]") ||
                normalized == "图片" -> "image"
            normalized.contains("[表情]") ||
                normalized == "表情" -> "emoji"
            else -> "text"
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val valueByte = byte.toInt() and 0xff
            chars[index * 2] = hex[valueByte ushr 4]
            chars[index * 2 + 1] = hex[valueByte and 0x0f]
        }
        return String(chars)
    }
}
