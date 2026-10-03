package com.lulu.luluaiagent.model.chatgpt;

import java.time.Instant;

public record ChatGptAccountStatus(
        boolean signedIn,
        boolean planUsageEnabled,
        String email,
        String subject,
        String clientId,
        Instant expiresAt
) {
    public static ChatGptAccountStatus signedOut() {
        return new ChatGptAccountStatus(
                false, false, null, null, null, null);
    }
}
