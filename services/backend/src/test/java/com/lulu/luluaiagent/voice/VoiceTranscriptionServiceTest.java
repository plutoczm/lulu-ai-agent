package com.lulu.luluaiagent.voice;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTranscriptionServiceTest {

    @Test
    void amrDefaultsToEightKhz() {
        assertEquals(
                8000,
                VoiceTranscriptionService.resolveSampleRate(
                        "amr",
                        null,
                        new byte[]{1, 2, 3}));
    }

    @Test
    void wavSampleRateIsReadFromHeader() {
        byte[] wav = new byte[44];
        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';
        ByteBuffer.wrap(wav, 24, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(16000);

        assertEquals(
                16000,
                VoiceTranscriptionService.resolveSampleRate(
                        "wav",
                        null,
                        wav));
    }

    @Test
    void unknownFormatIsRejectedInsteadOfGuessed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VoiceTranscriptionService.normalizeFormat("silk"));
        assertThrows(
                IllegalArgumentException.class,
                () -> VoiceTranscriptionService.resolveSampleRate(
                        "mp3",
                        null,
                        new byte[]{1}));
    }

    @Test
    void amrWidebandIsRejectedBeforeProviderCall() {
        byte[] wideband = "#!AMR-WB\nvoice".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> VoiceTranscriptionService.validateEncoding(
                        "amr",
                        wideband));

        assertTrue(error.getMessage().contains("AMR-WB"));
    }

    @Test
    void opusRequiresOggContainer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VoiceTranscriptionService.validateEncoding(
                        "opus",
                        new byte[]{1, 2, 3, 4}));

        assertDoesNotThrow(
                () -> VoiceTranscriptionService.validateEncoding(
                        "opus",
                        new byte[]{'O', 'g', 'g', 'S', 0}));
    }

    @Test
    void wavMustBePcm() {
        byte[] wav = new byte[44];
        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        wav[20] = 3;
        wav[21] = 0;

        assertThrows(
                IllegalArgumentException.class,
                () -> VoiceTranscriptionService.validateEncoding(
                        "wav",
                        wav));

        wav[20] = 1;
        assertDoesNotThrow(
                () -> VoiceTranscriptionService.validateEncoding(
                        "wav",
                        wav));
    }
}
