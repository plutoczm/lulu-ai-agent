package com.lulu.luluaiagent.model.chatgpt;

import java.time.Instant;
import java.util.List;

public record ChatGptCredential(
        String email,
        String issuer,
        String subject,
        String clientId,
        String extAgentHostId,
        String idToken,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> scopes,
        Instant savedAt
) {
    public ChatGptCredential {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public Instant expiresAt() {
        return savedAt.plusSeconds(expiresIn);
    }

    public boolean hasPlanUsageScope() {
        return scopes.contains("chatgpt.tokens.use.direct");
    }
}
