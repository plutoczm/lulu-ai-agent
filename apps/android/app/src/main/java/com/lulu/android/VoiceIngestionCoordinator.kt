package com.lulu.android

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object VoiceIngestionCoordinator {
    private const val TAG = "LuluVoice"

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lulu-voice-ingestion").apply {
            isDaemon = true
        }
    }

    fun scheduleFromNotification(
        context: Context,
        identity: ActiveIdentity,
        platform: String,
        notificationSourceKey: String,
        observedAtMillis: Long,
        audioUri: String = "",
        audioMimeType: String = ""
    ) {
        scheduleVoice(
            context = context,
            identity = identity,
            platform = platform,
            sourceKey = notificationSourceKey,
            observedAtMillis = observedAtMillis,
            audioUri = audioUri,
            audioMimeType = audioMimeType
        )
    }

    fun scheduleFromScreen(
        context: Context,
        identity: ActiveIdentity,
        platform: String,
        screenSourceKey: String,
        observedAtMillis: Long
    ) {
        scheduleVoice(
            context = context,
            identity = identity,
            platform = platform,
            sourceKey = screenSourceKey,
            observedAtMillis = observedAtMillis
        )
    }

    private fun scheduleVoice(
        context: Context,
        identity: ActiveIdentity,
        platform: String,
        sourceKey: String,
        observedAtMillis: Long,
        audioUri: String = "",
        audioMimeType: String = ""
    ) {
        val appContext = context.applicationContext
        if (!LuluPrefs.backgroundVoiceEnabled(appContext)) return

        VoiceIngestionQueue.enqueue(
            appContext,
            identity.account.id,
            platform,
            sourceKey,
            observedAtMillis,
            audioUri,
            audioMimeType
        )

        scheduleFlush(appContext, 500L)
    }

    fun flushPending(context: Context) {
        val appContext = context.applicationContext
        executor.execute { runFlush(appContext) }
    }

    private fun runFlush(appContext: Context) {
        val baseUrl = LuluPrefs.baseUrl(appContext)
        val cookie = LuluPrefs.cookie(appContext)
        if (baseUrl.isBlank() || cookie.isBlank()) return
        if (!LuluPrefs.backgroundVoiceEnabled(appContext)) return

        val jobs = VoiceIngestionQueue.pending(appContext)
        for (job in jobs) {
            val account = IdentityStore.accounts(appContext)
                .firstOrNull { it.id == job.accountId }
            if (account == null) {
                VoiceIngestionQueue.delete(appContext, job.id)
                continue
            }
            val person = IdentityStore.person(
                appContext,
                account.personId
            )
            if (person == null) {
                VoiceIngestionQueue.delete(appContext, job.id)
                continue
            }

            val asset = VoiceAudioSourceRegistry.resolve(
                appContext,
                job
            )
            if (asset == null) {
                VoiceIngestionQueue.markWaitingAudio(
                    appContext,
                    job.id,
                    "waiting for an accessible audio source"
                )
                Log.d(
                    TAG,
                    "voice waiting_audio account=" + account.id +
                        " platform=" + job.platform +
                        " attachment=" + job.audioUri.isNotBlank()
                )
                continue
            }

            try {
                val validationError = VoiceAudioSupport.validateForAsr(
                    asset.file,
                    asset.format
                )
                if (validationError != null) {
                    VoiceIngestionQueue.claimSource(
                        appContext,
                        asset.sourceId,
                        job.notificationSourceKey
                    )
                    VoiceIngestionQueue.markFailed(
                        appContext,
                        job.id,
                        validationError
                    )
                    Log.w(
                        TAG,
                        "voice job rejected account=" + account.id +
                            " source=" + asset.sourceId +
                            " reason=" + validationError
                    )
                    continue
                }

                val sampleRate = asset.sampleRate
                    ?: VoiceAudioSupport.detectSampleRate(
                        asset.file,
                        asset.format
                    )
                if (sampleRate == null &&
                    asset.format !in setOf("amr", "wav")
                ) {
                    VoiceIngestionQueue.claimSource(
                        appContext,
                        asset.sourceId,
                        job.notificationSourceKey
                    )
                    VoiceIngestionQueue.markFailed(
                        appContext,
                        job.id,
                        "sample rate unavailable for " + asset.format
                    )
                    continue
                }

                val audio = asset.file.readBytes()
                val hash = sha256(audio)
                val transcript = CoachApi.transcribeVoice(
                    baseUrl = baseUrl,
                    cookie = cookie,
                    audio = audio,
                    format = asset.format,
                    sampleRate = sampleRate
                )
                val observed = Instant.ofEpochMilli(
                    job.observedAtMillis.coerceAtLeast(1L)
                ).toString()

                ChatSyncOutbox.enqueue(
                    appContext,
                    account.id,
                    listOf(
                        ChatSyncMessage(
                            sender = "对方",
                            text = transcript.text,
                            time = observed,
                            contentType = "voice_transcript",
                            source = "background_voice_asr",
                            sourceKey = "voice-bg:" + hash,
                            replacesSourceKey = job.notificationSourceKey,
                            observedAt = observed
                        )
                    )
                )
                VoiceIngestionQueue.claimSource(
                    appContext,
                    asset.sourceId,
                    job.notificationSourceKey
                )
                VoiceIngestionQueue.delete(appContext, job.id)
                Log.i(
                    TAG,
                    "voice transcribed account=" + account.id +
                        " adapter=" + asset.sourceId.substringBefore(':') +
                        " format=" + asset.format +
                        " sampleRate=" + (sampleRate ?: 0) +
                        " pending=" + VoiceIngestionQueue.count(appContext)
                )
                ChatSyncCoordinator.flushPending(appContext)
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "voice ingestion failed account=" + account.id,
                    error
                )
                defer(
                    appContext,
                    job,
                    error.message ?: error.javaClass.simpleName
                )
            } finally {
                asset.file.delete()
            }
        }

        val nextRetryAt = VoiceIngestionQueue.nextRetryAtMillis(appContext)
        if (nextRetryAt != null) {
            val delay = (nextRetryAt - System.currentTimeMillis())
                .coerceAtLeast(250L)
            scheduleFlush(appContext, delay)
        }
    }

    private fun defer(
        context: Context,
        job: VoiceIngestionJob,
        reason: String
    ) {
        val decision = VoiceIngestionQueue.markAttempt(
            context,
            job.id,
            reason
        )
        if (decision.exhausted) {
            Log.w(
                TAG,
                "voice job exhausted account=" + job.accountId +
                    " source=" + job.notificationSourceKey +
                    " reason=" + reason
            )
            return
        }

        val retryAt = decision.retryAtMillis ?: return
        val delay = (retryAt - System.currentTimeMillis())
            .coerceAtLeast(250L)
        Log.d(
            TAG,
            "voice retry persisted account=" + job.accountId +
                " attempt=" + (job.attempts + 1) +
                " delay=" + delay + "ms reason=" + reason
        )
    }

    private fun scheduleFlush(
        context: Context,
        delayMillis: Long
    ) {
        val appContext = context.applicationContext
        executor.schedule(
            { runFlush(appContext) },
            delayMillis.coerceAtLeast(0L),
            TimeUnit.MILLISECONDS
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = "0123456789abcdef"
        val chars = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = hex[value ushr 4]
            chars[index * 2 + 1] = hex[value and 0x0f]
        }
        return String(chars)
    }
}
