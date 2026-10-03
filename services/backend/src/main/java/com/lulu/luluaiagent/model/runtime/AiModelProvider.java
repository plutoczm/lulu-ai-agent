package com.lulu.luluaiagent.model.runtime;

import java.util.Collection;
import java.util.Optional;

public interface AiModelProvider {

    String id();

    String name();

    boolean available();

    Collection<RegisteredModel> models();

    Optional<RegisteredModel> model(String modelId);

    RegisteredModel defaultModel();
}
