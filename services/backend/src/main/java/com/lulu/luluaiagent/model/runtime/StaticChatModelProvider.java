package com.lulu.luluaiagent.model.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StaticChatModelProvider implements AiModelProvider {

    private final String id;
    private final String name;
    private final Map<String, RegisteredModel> models;
    private final String defaultModelId;

    public StaticChatModelProvider(
            String id,
            String name,
            Collection<RegisteredModel> models,
            String defaultModelId) {
        this.id = id;
        this.name = name;
        this.models = new LinkedHashMap<>();
        for (RegisteredModel model : models) {
            this.models.put(model.descriptor().modelId(), model);
        }
        this.defaultModelId = defaultModelId;
    }
    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean available() {
        return !models.isEmpty();
    }

    @Override
    public Collection<RegisteredModel> models() {
        return java.util.List.copyOf(models.values());
    }
    @Override
    public Optional<RegisteredModel> model(String modelId) {
        return Optional.ofNullable(models.get(modelId));
    }

    @Override
    public RegisteredModel defaultModel() {
        RegisteredModel model = models.get(defaultModelId);
        if (model == null) {
            throw new IllegalStateException(
                    "Default model is not registered: "
                            + id + "/" + defaultModelId);
        }
        return model;
    }
}
