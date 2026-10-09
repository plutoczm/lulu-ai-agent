package com.lulu.android

import android.app.Notification
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Background incoming-message ingestion.
 *
 * The system only receives notifications after the user explicitly enables
 * notification access. Unknown/unbound contact titles are ignored.
 */
class LuluNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ChatSyncCoordinator.flushPending(this)
        VoiceIngestionCoordinator.flushPending(this)
    }

    companion object {
        private const val TAG = "LuluSync"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val platform = when (notification.packageName) {
            WECHAT_PACKAGE -> "wechat"
            QQ_PACKAGE -> "qq"
            else -> return
        }

        val extras = notification.notification.extras
        val chatTitle = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.trim()

        if (chatTitle.isNullOrBlank()) return

        val messages = messagingStyleMessages(extras).ifEmpty {
            val fallback = extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extras
                    .getCharSequence(Notification.EXTRA_TEXT)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

            if (fallback == null) emptyList()
            else listOf(
                NotificationMessage(
                    text = fallback,
                    timeMillis = notification.postTime
                )
            )
        }

        messages
            .filterNot { isGenericNotification(it.text) }
            .forEachIndexed { index, message ->
                val messageTime = message.timeMillis
                    .takeIf { it > 0L }
                    ?: notification.postTime
                val sourceKey = buildString {
                    append(notification.key)
                    append(':')
                    append(messageTime)
                    append(':')
                    append(index)
                    append(':')
                    append(message.text.hashCode())
                }

                ChatSyncCoordinator.syncNotification(
                    context = this,
                    platform = platform,
                    chatTitle = chatTitle,
                    text = message.text,
                    notificationKey = sourceKey,
                    observedAtMillis = messageTime,
                    audioUri = message.audioUri,
                    audioMimeType = message.audioMimeType
                )
            }
    }

    private data class NotificationMessage(
        val text: String,
        val timeMillis: Long,
        val audioUri: String = "",
        val audioMimeType: String = ""
    )

    private fun messagingStyleMessages(
        extras: Bundle
    ): List<NotificationMessage> {
        val bundles = extras.getParcelableArray(
            Notification.EXTRA_MESSAGES
        ) ?: return emptyList()

        return bundles
            .asSequence()
            .mapNotNull { it as? Bundle }
            .mapNotNull { bundle ->
                val text = bundle.getCharSequence("text")
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val dataUri = runCatching {
                    bundle.getParcelable("uri") as? Uri
                }.getOrNull()
                val dataMimeType = bundle
                    .getString("type")
                    .orEmpty()
                    .takeIf { it.startsWith("audio/") }
                    .orEmpty()

                NotificationMessage(
                    text = text,
                    timeMillis = bundle.getLong("time", 0L),
                    audioUri = if (dataMimeType.isNotBlank()) {
                        dataUri?.toString().orEmpty()
                    } else {
                        ""
                    },
                    audioMimeType = dataMimeType
                )
            }
            .toList()
    }

    private fun isGenericNotification(text: String): Boolean {
        val normalized = text.replace(" ", "")
        val generic = normalized in setOf(
            "你收到了一条消息",
            "收到一条消息",
            "有新消息",
            "新消息"
        )
        if (generic) {
            Log.d(TAG, "generic notification skipped")
        }
        return generic
    }
}
