package com.lulu.luluaiagent.coach;

/**
 * Minimal structured output used by latency-sensitive mobile coaching.
 */
public record QuickConversationCoachResponse(
        boolean needsClarification,
        String clarificationQuestion,
        String aggressive,
        String normal,
        String conservative
) {
}
