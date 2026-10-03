package com.lulu.luluaiagent.model.chatgpt;

public record ChatGptLoginStart(
        String authorizationUrl,
        String attemptId,
        String callbackUrl
) {
}
