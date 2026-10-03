package com.lulu.luluaiagent.model.chatgpt;

import com.lulu.luluaiagent.model.runtime.AiModelProvider;
import com.lulu.luluaiagent.model.runtime.ModelCapability;
import com.lulu.luluaiagent.model.runtime.ModelDescriptor;
import com.lulu.luluaiagent.model.runtime.RegisteredModel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ChatGptPlanProvider implements AiModelProvider {

    public static final String PROVIDER_ID = "openai-chatgpt";

    private final ChatGptOAuthService oauth;
    private final ChatGptPlanClient client;

    private volatile List<ChatGptModelInfo> cachedModels = List.of();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public ChatGptPlanProvider(
            ChatGptOAuthService oauth,
            ChatGptPlanClient client) {
        this.oauth = oauth;
        this.client = client;
    }
    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public String name() {
        return "OpenAI ChatGPT";
    }

    @Override
    public boolean available() {
        ChatGptAccountStatus status = oauth.status();
        return status.signedIn() && status.planUsageEnabled();
    }

    @Override
    public Collection<RegisteredModel> models() {
        if (!available()) {
            return List.of();
        }
        return catalog().stream()
                .map(this::registered)
                .toList();
    }

    @Override
    public Optional<RegisteredModel> model(String modelId) {
        if (!available()) {
            return Optional.empty();
        }
        return catalog().stream()
                .filter(item -> item.slug().equals(modelId))
                .findFirst()
                .map(this::registered);
    }

    @Override
    public RegisteredModel defaultModel() {
        return models().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ChatGPT plan model is available."));
    }

    public synchronized List<ChatGptModelInfo> refresh() {
        if (!available()) {
            cachedModels = List.of();
            cacheExpiresAt = Instant.EPOCH;
            return cachedModels;
        }
        cachedModels = client.listModels();
        cacheExpiresAt = Instant.now().plusSeconds(60);
        return cachedModels;
    }

    private List<ChatGptModelInfo> catalog() {
        if (Instant.now().isAfter(cacheExpiresAt)) {
            return refresh();
        }
        return cachedModels;
    }
    private RegisteredModel registered(ChatGptModelInfo info) {
        ModelDescriptor descriptor = new ModelDescriptor(
                PROVIDER_ID,
                name(),
                info.slug(),
                info.displayName(),
                "openai-responses",
                false,
                Set.of(
                        ModelCapability.CHAT,
                        ModelCapability.STREAMING,
                        ModelCapability.TOOLS,
                        ModelCapability.STRUCTURED_OUTPUT));

        return new RegisteredModel(
                descriptor,
                new ChatGptPlanChatModel(info.slug(), client));
    }
}
