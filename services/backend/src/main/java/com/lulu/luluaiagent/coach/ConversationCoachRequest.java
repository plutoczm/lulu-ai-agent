package com.lulu.luluaiagent.coach;

import java.util.List;

/**
 * Platform-neutral conversation context for reply coaching.
 *
 * personId scopes long-term relationship memory.
 * accountId identifies the concrete WeChat/QQ account.
 * conversationId scopes the recent-message thread for that account.
 */
public record ConversationCoachRequest(
        String platform,
        String conversationId,
        String personId,
        String accountId,
        String userAlias,
        String otherAlias,
        String relationshipStage,
        String goal,
        String userStyle,
        List<Message> messages
) {

    public record Message(
            String sender,
            String text,
            String time
    ) {
    }
}
