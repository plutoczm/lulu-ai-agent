package com.lulu.luluaiagent.coach;

import java.util.List;

/**
 * Platform-neutral conversation context for reply coaching.
 */
public record ConversationCoachRequest(
        String platform,
        String conversationId,
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
