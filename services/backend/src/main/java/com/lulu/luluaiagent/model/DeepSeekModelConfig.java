package com.lulu.luluaiagent.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.models.deepseek",
        name = "enabled",
        havingValue = "true"
)
public class DeepSeekModelConfig {

    @Bean("deepSeekChatModel")
    public ChatModel deepSeekChatModel(
            @Value("${app.models.deepseek.api-key}") String apiKey,
            @Value("${app.models.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.models.deepseek.model:deepseek-flash}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "DEEPSEEK_ENABLED=true but DEEPSEEK_API_KEY is empty.");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
