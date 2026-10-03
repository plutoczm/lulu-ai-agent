package com.lulu.luluaiagent.model.runtime;

import java.util.Set;

public record ModelDescriptor(
        String providerId,
        String providerName,
        String modelId,
        String displayName,
        String api,
        boolean local,
        Set<ModelCapability> capabilities
) {
    public ModelDescriptor {
        capabilities = Set.copyOf(capabilities);
    }

    public String key() {
        return providerId + "/" + modelId;
    }
}
