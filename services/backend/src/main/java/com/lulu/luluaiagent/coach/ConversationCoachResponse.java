package com.lulu.luluaiagent.coach;

import java.util.List;

/**
 * Structured reply plan shared by Web, QQ and WeChat adapters.
 */
public record ConversationCoachResponse(
        boolean needsClarification,
        String clarificationQuestion,
        String mainStrategy,
        String diagnosis,
        String bestReply,
        List<ReplyOption> alternatives,
        Branches branches,
        String nextStep,
        List<String> facts,
        List<String> uncertainties,
        List<String> safetyNotes
) {

    public record ReplyOption(
            String label,
            String text,
            String tradeoff
    ) {
    }

    public record Branches(
            String positive,
            String ambiguous,
            String reject
    ) {
    }
}
