package com.lulu.luluaiagent.model;

import com.lulu.luluaiagent.model.runtime.ModelDescriptor;
import com.lulu.luluaiagent.model.runtime.ModelRegistry;
import com.lulu.luluaiagent.model.runtime.ModelRoute;
import com.lulu.luluaiagent.model.runtime.RegisteredModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ModelRouter {

    private final ModelRegistry registry;

    public ModelRouter(ModelRegistry registry) {
        this.registry = registry;
    }

    public ChatModel coachPrimaryModel() {
        return model(ModelRoute.COACH).chatModel();
    }

    public ChatModel agentPrimaryModel() {
        return model(ModelRoute.AGENT).chatModel();
    }
    public ChatModel fastModel() {
        return model(ModelRoute.FAST).chatModel();
    }

    public ChatModel memoryModel() {
        return model(ModelRoute.MEMORY).chatModel();
    }

    public boolean deepSeekAvailable() {
        return registry.hasProvider("deepseek");
    }

    public String coachPrimaryModelName() {
        return model(ModelRoute.COACH).descriptor().modelId();
    }

    public String agentPrimaryModelName() {
        return model(ModelRoute.AGENT).descriptor().modelId();
    }
    public String fastModelName() {
        return model(ModelRoute.FAST).descriptor().modelId();
    }

    public String memoryModelName() {
        return model(ModelRoute.MEMORY).descriptor().modelId();
    }

    public List<ModelDescriptor> catalog() {
        return registry.models();
    }

    public Map<String, String> routes() {
        return registry.resolvedRoutes();
    }

    public Map<String, String> preferredRoutes() {
        return registry.preferredRoutes();
    }

    public ModelDescriptor select(
            String routeId,
            String candidate) {
        ModelRoute route = route(routeId);
        return registry
                .setPreferredRoute(route, candidate)
                .descriptor();
    }

    public void clearSelection(String routeId) {
        registry.clearPreferredRoute(route(routeId));
    }

    private ModelRoute route(String routeId) {
        for (ModelRoute route : ModelRoute.values()) {
            if (route.id().equalsIgnoreCase(routeId)) {
                return route;
            }
        }
        throw new IllegalArgumentException(
                "Unknown model route: " + routeId);
    }

    private RegisteredModel model(ModelRoute route) {
        return registry.resolve(route);
    }
}
