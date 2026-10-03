package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import com.lulu.luluaiagent.coach.ConversationCoachResponse;
import com.lulu.luluaiagent.coach.ConversationCoachService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/channel/coach")
public class ChannelCoachController {

    private final ConversationCoachService coachService;
    private final String sharedToken;

    public ChannelCoachController(
            ConversationCoachService coachService,
            @Value("${app.channels.shared-token:}") String sharedToken) {
        this.coachService = coachService;
        this.sharedToken = sharedToken == null ? "" : sharedToken;
    }

    @PostMapping("/suggest")
    public ConversationCoachResponse suggest(
            @RequestHeader(value = "X-Lulu-Channel-Token", required = false) String token,
            @RequestBody ConversationCoachRequest request) {
        requireChannelToken(token);
        ConversationCoachRequest scoped = new ConversationCoachRequest(
                request.platform(),
                scopeConversationId(request.conversationId()),
                request.userAlias(),
                request.otherAlias(),
                request.relationshipStage(),
                request.goal(),
                request.userStyle(),
                request.messages());
        return coachService.suggest(scoped);
    }

    private void requireChannelToken(String token) {
        if (sharedToken.isBlank() || token == null || token.isBlank()) {
            throw new SecurityException("Channel authentication is required.");
        }
        byte[] expected = sharedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("Invalid channel authentication.");
        }
    }

    private String scopeConversationId(String conversationId) {
        String value = (conversationId == null || conversationId.isBlank())
                ? "default"
                : conversationId.trim();
        value = value.replaceAll("[^A-Za-z0-9_-]", "_");
        if (value.length() > 128) {
            value = value.substring(0, 128);
        }
        return "channel__" + value;
    }
}
