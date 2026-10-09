package com.lulu.luluaiagent.voice;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
public class VoiceTranscriptionService {
    private static final Set<String> SUPPORTED_FORMATS =
            Set.of("amr", "wav", "mp3", "aac", "opus", "speex", "pcm");
    private static final int DEFAULT_MAX_BYTES = 6 * 1024 * 1024;

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final int maxBytes;

    public VoiceTranscriptionService(
            @Value("${app.voice.asr.enabled:true}") boolean enabled,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${app.voice.asr.model:paraformer-realtime-v2}") String model,
            @Value("${app.voice.asr.max-bytes:6291456}") int maxBytes) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
    }

    public TranscriptionResult transcribe(
            String audioBase64,
            String rawFormat,
            Integer requestedSampleRate) {
        if (!enabled) {
            throw new IllegalStateException("Voice ASR is disabled");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is required for voice ASR");
        }
        if (!StringUtils.hasText(audioBase64)) {
            throw new IllegalArgumentException("audioBase64 is required");
        }

        String format = normalizeFormat(rawFormat);
        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("audioBase64 is invalid", error);
        }
        if (audio.length == 0) {
            throw new IllegalArgumentException("audio payload is empty");
        }
        if (audio.length > maxBytes) {
            throw new IllegalArgumentException("audio payload exceeds " + maxBytes + " bytes");
        }

        validateEncoding(format, audio);
        int sampleRate = resolveSampleRate(format, requestedSampleRate, audio);
        File temp = null;
        Recognition recognizer = new Recognition();
        try {
            temp = File.createTempFile("lulu-voice-", "." + format);
            Files.write(temp.toPath(), audio);

            RecognitionParam param = RecognitionParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .format(format)
                    .sampleRate(sampleRate)
                    .parameter("language_hints", new String[]{"zh", "en"})
                    .build();

            String text = recognizer.call(param, temp);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("ASR returned an empty transcript");
            }
            return new TranscriptionResult(
                    text.trim(),
                    "dashscope",
                    model,
                    format,
                    sampleRate);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Voice transcription failed", error);
        } finally {
            try {
                recognizer.getDuplexApi().close(1000, "bye");
            } catch (Exception ignored) {
            }
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }

    static String normalizeFormat(String rawFormat) {
        String format = rawFormat == null
                ? ""
                : rawFormat.trim().toLowerCase(Locale.ROOT).replace(".", "");
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new IllegalArgumentException(
                    "Unsupported audio format: " + (rawFormat == null ? "" : rawFormat));
        }
        return format;
    }

    static void validateEncoding(String format, byte[] audio) {
        if ("amr".equals(format)) {
            String prefix = new String(
                    audio,
                    0,
                    Math.min(audio.length, 9),
                    java.nio.charset.StandardCharsets.US_ASCII);
            if (prefix.startsWith("#!AMR-WB")) {
                throw new IllegalArgumentException(
                        "AMR-WB is unsupported; DashScope realtime ASR accepts AMR-NB");
            }
            if (!prefix.startsWith("#!AMR\n")) {
                throw new IllegalArgumentException("Invalid or unsupported AMR container");
            }
        }

        if ("wav".equals(format)) {
            if (audio.length < 22
                    || audio[0] != 'R'
                    || audio[1] != 'I'
                    || audio[2] != 'F'
                    || audio[3] != 'F'
                    || audio[8] != 'W'
                    || audio[9] != 'A'
                    || audio[10] != 'V'
                    || audio[11] != 'E') {
                throw new IllegalArgumentException("Invalid WAV container");
            }
            int audioFormat = (audio[20] & 0xff) | ((audio[21] & 0xff) << 8);
            if (audioFormat != 1) {
                throw new IllegalArgumentException(
                        "Only PCM WAV is supported by configured DashScope ASR");
            }
        }

        if ("opus".equals(format)) {
            boolean ogg = audio.length >= 4
                    && audio[0] == 'O'
                    && audio[1] == 'g'
                    && audio[2] == 'g'
                    && audio[3] == 'S';
            if (!ogg) {
                throw new IllegalArgumentException(
                        "Opus must use Ogg encapsulation for configured DashScope ASR");
            }
        }
    }

    static int resolveSampleRate(
            String format,
            Integer requestedSampleRate,
            byte[] audio) {
        if (requestedSampleRate != null && requestedSampleRate > 0) {
            return requestedSampleRate;
        }
        if ("amr".equals(format)) {
            return 8000;
        }
        if ("wav".equals(format)) {
            Integer wavRate = parseWavSampleRate(audio);
            if (wavRate != null) {
                return wavRate;
            }
        }
        throw new IllegalArgumentException(
                "sampleRate is required for audio format " + format);
    }

    private static Integer parseWavSampleRate(byte[] audio) {
        if (audio == null || audio.length < 28) return null;
        if (audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F') {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(audio, 24, 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        int rate = buffer.getInt();
        return rate > 0 ? rate : null;
    }

    public record TranscriptionResult(
            String text,
            String provider,
            String model,
            String format,
            int sampleRate
    ) {}
}
