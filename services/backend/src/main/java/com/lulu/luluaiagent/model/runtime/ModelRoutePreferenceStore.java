package com.lulu.luluaiagent.model.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lulu.luluaiagent.config.ProjectPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ModelRoutePreferenceStore {

    private final ObjectMapper objectMapper;
    private final Path file =
            ProjectPaths.data("model-settings", "routes.json");

    public ModelRoutePreferenceStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized Optional<String> get(ModelRoute route) {
        return Optional.ofNullable(readAll().get(route.id()));
    }
    public synchronized void set(
            ModelRoute route,
            String candidate) {
        Map<String, String> values = readAll();
        values.put(route.id(), candidate);
        writeAll(values);
    }

    public synchronized void clear(ModelRoute route) {
        Map<String, String> values = readAll();
        values.remove(route.id());
        writeAll(values);
    }

    public synchronized Map<String, String> snapshot() {
        return Map.copyOf(readAll());
    }

    private Map<String, String> readAll() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<LinkedHashMap<String, String>>() {});
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read model route preferences.", e);
        }
    }
    private void writeAll(Map<String, String> values) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(
                    file.getFileName() + ".tmp");
            String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(values);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write model route preferences.", e);
        }
    }
}
