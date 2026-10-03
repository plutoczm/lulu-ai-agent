package com.lulu.luluaiagent.model.chatgpt;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public class ChatGptPlanChatModel implements ChatModel {

    private final String modelId;
    private final ChatGptPlanClient client;

    public ChatGptPlanChatModel(
            String modelId,
            ChatGptPlanClient client) {
        this.modelId = modelId;
        this.client = client;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = client.complete(modelId, prompt);
        return response(text);
    }
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink ->
                Schedulers.boundedElastic().schedule(() -> {
                    try {
                        client.stream(modelId, prompt, delta -> {
                            if (!sink.isCancelled()) {
                                sink.next(response(delta));
                            }
                        });
                        if (!sink.isCancelled()) {
                            sink.complete();
                        }
                    }
                    catch (Exception e) {
                        if (!sink.isCancelled()) {
                            sink.error(e);
                        }
                    }
                }));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder()
                .model(modelId)
                .build();
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(text))));
    }
}
