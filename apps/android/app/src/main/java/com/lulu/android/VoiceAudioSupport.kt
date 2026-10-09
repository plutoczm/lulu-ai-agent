package com.lulu.android

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File

object VoiceAudioSupport {
    private const val TAG = "LuluVoice"
    private const val MAX_AUDIO_BYTES = 8L * 1024L * 1024L

    fun formatFrom(mimeType: String?, uri: Uri?): String? {
        val mime = mimeType.orEmpty().lowercase()
        val fromMime = when {
            mime.contains("amr") -> "amr"
            mime.contains("wav") || mime.contains("wave") -> "wav"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("aac") -> "aac"
            mime.contains("opus") -> "opus"
            else -> null
        }
        if (fromMime != null) return fromMime

        val path = uri?.lastPathSegment.orEmpty()
        return path.substringAfterLast('.', "")
            .lowercase()
            .takeIf {
                it in setOf("amr", "wav", "mp3", "aac", "opus")
            }
    }

    fun validateForAsr(file: File, format: String): String? {
        if (!file.exists()) return "audio file missing"
        if (file.length() <= 0L) return "audio file is empty"
        if (file.length() > MAX_AUDIO_BYTES) return "audio file exceeds 8 MB"

        val normalized = format.lowercase()
        if (normalized !in setOf("amr", "wav", "mp3", "aac", "opus")) {
            return "unsupported audio format: " + normalized
        }

        val header = runCatching {
            file.inputStream().use { stream ->
                val bytes = ByteArray(44)
                val read = stream.read(bytes)
                if (read <= 0) ByteArray(0) else bytes.copyOf(read)
            }
        }.getOrElse {
            return "unable to read audio header"
        }

        if (normalized == "amr") {
            val prefix = String(header, Charsets.US_ASCII)
            return when {
                prefix.startsWith("#!AMR-WB") ->
                    "AMR-WB is unsupported by configured ASR"
                prefix.startsWith("#!AMR\n") -> null
                else -> "invalid or unsupported AMR container"
            }
        }

        if (normalized == "wav") {
            if (header.size < 22 ||
                String(header.copyOfRange(0, 4), Charsets.US_ASCII) != "RIFF" ||
                String(header.copyOfRange(8, 12), Charsets.US_ASCII) != "WAVE"
            ) {
                return "invalid WAV container"
            }
            val audioFormat =
                (header[20].toInt() and 0xff) or
                    ((header[21].toInt() and 0xff) shl 8)
            if (audioFormat != 1) {
                return "only PCM WAV is supported by configured ASR"
            }
        }

        if (normalized == "opus") {
            val oggHeader = header.size >= 4 &&
                String(
                    header.copyOfRange(0, 4),
                    Charsets.US_ASCII
                ) == "OggS"
            if (!oggHeader) {
                return "Opus must use Ogg encapsulation"
            }
        }

        return null
    }

    fun detectSampleRate(file: File, format: String): Int? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mediaFormat = extractor.getTrackFormat(index)
                val mime = mediaFormat
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                if (!mime.startsWith("audio/")) continue
                if (!mediaFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    continue
                }
                val rate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (rate in 4_000..192_000) return rate
            }
        } catch (error: Exception) {
            Log.d(TAG, "sample-rate probe failed format=" + format, error)
        } finally {
            runCatching { extractor.release() }
        }

        return detectHeaderSampleRate(file, format)
    }

    private fun detectHeaderSampleRate(file: File, format: String): Int? {
        val normalized = format.lowercase()
        return runCatching {
            val header = file.inputStream().use { stream ->
                val bytes = ByteArray(32)
                val read = stream.read(bytes)
                if (read <= 0) ByteArray(0) else bytes.copyOf(read)
            }

            if (normalized == "amr") {
                val prefix = String(header, Charsets.US_ASCII)
                return@runCatching when {
                    prefix.startsWith("#!AMR-WB") -> 16_000
                    prefix.startsWith("#!AMR") -> 8_000
                    else -> null
                }
            }

            if (normalized == "wav" &&
                header.size >= 28 &&
                String(header.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF"
            ) {
                val rate =
                    (header[24].toInt() and 0xff) or
                        ((header[25].toInt() and 0xff) shl 8) or
                        ((header[26].toInt() and 0xff) shl 16) or
                        ((header[27].toInt() and 0xff) shl 24)
                return@runCatching rate.takeIf { it in 4_000..192_000 }
            }
            null
        }.getOrNull()
    }
}
