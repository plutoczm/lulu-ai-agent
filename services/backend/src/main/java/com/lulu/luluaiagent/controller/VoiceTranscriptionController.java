package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.voice.VoiceTranscriptionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/voice")
public class VoiceTranscriptionController {
    private final VoiceTranscriptionService transcriptionService;

    public VoiceTranscriptionController(
            VoiceTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping("/transcribe")
    public VoiceTranscriptionService.TranscriptionResult transcribe(
            @RequestBody VoiceTranscriptionRequest request) {
        return transcriptionService.transcribe(
                request.audioBase64(),
                request.format(),
                request.sampleRate());
    }

    public record VoiceTranscriptionRequest(
            String audioBase64,
            String format,
            Integer sampleRate
    ) {}
}
