package com.lulu.luluaiagent.model.runtime;

import org.springframework.ai.chat.model.ChatModel;

public record RegisteredModel(
        ModelDescriptor descriptor,
        ChatModel chatModel
) {
}
