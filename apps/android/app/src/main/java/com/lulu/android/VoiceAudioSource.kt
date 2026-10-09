package com.lulu.android

import android.content.Context
import android.net.Uri
import java.io.File

data class VoiceAudioAsset(
    val sourceId: String,
    val file: File,
    val format: String,
    val sampleRate: Int?
)

interface VoiceAudioSourceAdapter {
    val id: String

    fun resolve(
        context: Context,
        job: VoiceIngestionJob
    ): VoiceAudioAsset?
}

/**
 * Background-first adapter. It only uses an audio Uri that Android already
 * exposed to the notification listener. No UI interaction or private app
 * storage access is required.
 */
object NotificationAttachmentVoiceSource : VoiceAudioSourceAdapter {
    override val id: String = "notification_attachment"

    override fun resolve(
        context: Context,
        job: VoiceIngestionJob
    ): VoiceAudioAsset? {
        if (job.audioUri.isBlank()) return null

        val uri = runCatching { Uri.parse(job.audioUri) }.getOrNull()
            ?: return null
        val format = VoiceAudioSupport.formatFrom(
            job.audioMimeType,
            uri
        ) ?: return null
        val target = File(
            context.cacheDir,
            "voice_job_" + job.id + "." + format
        )

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            VoiceAudioAsset(
                sourceId = id + ":" + job.audioUri,
                file = target,
                format = format,
                sampleRate = VoiceAudioSupport.detectSampleRate(
                    target,
                    format
                )
            )
        } catch (_: SecurityException) {
            target.delete()
            null
        } catch (_: Exception) {
            target.delete()
            null
        }
    }
}

object VoiceAudioSourceRegistry {
    private val adapters: List<VoiceAudioSourceAdapter> = listOf(
        NotificationAttachmentVoiceSource
    )

    fun resolve(
        context: Context,
        job: VoiceIngestionJob
    ): VoiceAudioAsset? {
        for (adapter in adapters) {
            adapter.resolve(context, job)?.let { return it }
        }
        return null
    }
}
