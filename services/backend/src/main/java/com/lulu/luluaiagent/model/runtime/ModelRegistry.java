package com.lulu.luluaiagent.model.runtime;

import com.lulu.luluaiagent.model.chatgpt.ChatGptPlanProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ModelRegistry {

    private final ChatModel dashscopeChatModel;
    private final ChatModel ollamaChatModel;
    private final ChatGptPlanProvider chatGptPlanProvider;
    private final ModelRoutePreferenceStore preferenceStore;

    @Autowired(required = false)
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;
    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String dashscopeModelId;

    @Value("${spring.ai.ollama.chat.model:qwen3:8b}")
    private String ollamaModelId;

    @Value("${app.models.deepseek.model:deepseek-flash}")
    private String deepSeekModelId;

    @Value("${app.models.routes.coach:deepseek,dashscope}")
    private String coachRoute;

    @Value("${app.models.routes.agent:deepseek,dashscope}")
    private String agentRoute;

    @Value("${app.models.routes.fast:dashscope,deepseek}")
    private String fastRoute;

    @Value("${app.models.routes.memory:ollama}")
    private String memoryRoute;

    private final Map<String, AiModelProvider> providers =
            new LinkedHashMap<>();
    private final Map<ModelRoute, List<String>> routes =
            new EnumMap<>(ModelRoute.class);
    public ModelRegistry(
            @Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel,
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            ChatGptPlanProvider chatGptPlanProvider,
            ModelRoutePreferenceStore preferenceStore) {
        this.dashscopeChatModel = dashscopeChatModel;
        this.ollamaChatModel = ollamaChatModel;
        this.chatGptPlanProvider = chatGptPlanProvider;
        this.preferenceStore = preferenceStore;
    }

    @PostConstruct
    void initialize() {
        registerDashScope();
        registerOllama();
        if (deepSeekChatModel != null) {
            registerDeepSeek();
        }
        providers.put(
                ChatGptPlanProvider.PROVIDER_ID,
                chatGptPlanProvider);

        routes.put(ModelRoute.COACH, parseRoute(coachRoute));
        routes.put(ModelRoute.AGENT, parseRoute(agentRoute));
        routes.put(ModelRoute.FAST, parseRoute(fastRoute));
        routes.put(ModelRoute.MEMORY, parseRoute(memoryRoute));
    }

    public RegisteredModel resolve(ModelRoute route) {
        String preferred =
                preferenceStore.get(route).orElse(null);
        if (preferred != null) {
            RegisteredModel selected = resolveCandidate(preferred);
            if (selected != null && compatible(route, selected)) {
                return selected;
            }
        }

        List<String> candidates = routes.getOrDefault(route, List.of());
        for (String candidate : candidates) {
            RegisteredModel model = resolveCandidate(candidate);
            if (model != null && compatible(route, model)) {
                return model;
            }
        }
        throw new IllegalStateException(
                "No available model for route " + route.id()
                        + "; candidates=" + candidates);
    }

    public boolean hasProvider(String providerId) {
        AiModelProvider provider = providers.get(providerId);
        return provider != null && provider.available();
    }

    public List<ModelDescriptor> models() {
        return providers.values().stream()
                .flatMap(provider -> provider.models().stream())
                .map(RegisteredModel::descriptor)
                .toList();
    }

    public Map<String, String> resolvedRoutes() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ModelRoute route : ModelRoute.values()) {
            result.put(route.id(), resolve(route).descriptor().key());
        }
        return result;
    }

    public Map<String, String> preferredRoutes() {
        return preferenceStore.snapshot();
    }

    public RegisteredModel setPreferredRoute(
            ModelRoute route,
            String candidate) {
        RegisteredModel model = resolveCandidate(candidate);
        if (model == null) {
            throw new IllegalArgumentException(
                    "Model is not available: " + candidate);
        }
        if (!compatible(route, model)) {
            throw new IllegalArgumentException(
                    "Model does not support route " + route.id());
        }
        preferenceStore.set(route, candidate);
        return model;
    }

    public void clearPreferredRoute(ModelRoute route) {
        preferenceStore.clear(route);
    }

    private RegisteredModel resolveCandidate(String candidate) {
        String normalized = candidate.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String[] parts = normalized.split("/", 2);
        AiModelProvider provider = providers.get(parts[0]);
        if (provider == null || !provider.available()) {
            return null;
        }

        if (parts.length == 1 || parts[1].isBlank()) {
            return provider.defaultModel();
        }
        return provider.model(parts[1]).orElse(null);
    }

    private boolean compatible(
            ModelRoute route,
            RegisteredModel model) {
        Set<ModelCapability> capabilities =
                model.descriptor().capabilities();

        return switch (route) {
            case AGENT -> capabilities.contains(ModelCapability.TOOLS);
            case COACH -> capabilities.contains(
                    ModelCapability.STRUCTURED_OUTPUT);
            case FAST, MEMORY -> capabilities.contains(
                    ModelCapability.CHAT);
        };
    }

    private List<String> parseRoute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return List.copyOf(result);
    }
    private void registerDashScope() {
        ModelDescriptor descriptor = new ModelDescriptor(
                "dashscope",
                "Alibaba DashScope",
                dashscopeModelId,
                dashscopeModelId,
                "dashscope",
                false,
                Set.of(
                        ModelCapability.CHAT,
                        ModelCapability.STREAMING,
                        ModelCapability.TOOLS,
                        ModelCapability.STRUCTURED_OUTPUT));
        registerSingle(descriptor, dashscopeChatModel);
    }

    private void registerOllama() {
        ModelDescriptor descriptor = new ModelDescriptor(
                "ollama",
                "Ollama",
                ollamaModelId,
                ollamaModelId,
                "ollama",
                true,
                Set.of(
                        ModelCapability.CHAT,
                        ModelCapability.LOCAL));
        registerSingle(descriptor, ollamaChatModel);
    }
    private void registerDeepSeek() {
        ModelDescriptor descriptor = new ModelDescriptor(
                "deepseek",
                "DeepSeek",
                deepSeekModelId,
                deepSeekModelId,
                "openai-compatible",
                false,
                Set.of(
                        ModelCapability.CHAT,
                        ModelCapability.STREAMING,
                        ModelCapability.TOOLS,
                        ModelCapability.STRUCTURED_OUTPUT));
        registerSingle(descriptor, deepSeekChatModel);
    }

    private void registerSingle(
            ModelDescriptor descriptor,
            ChatModel chatModel) {
        RegisteredModel registered =
                new RegisteredModel(descriptor, chatModel);
        providers.put(
                descriptor.providerId(),
                new StaticChatModelProvider(
                        descriptor.providerId(),
                        descriptor.providerName(),
                        List.of(registered),
                        descriptor.modelId()));
    }
}
