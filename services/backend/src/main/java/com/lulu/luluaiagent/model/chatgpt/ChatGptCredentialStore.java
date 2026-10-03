package com.lulu.luluaiagent.model.chatgpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lulu.luluaiagent.config.ProjectPaths;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
public class ChatGptCredentialStore {

    private final ObjectMapper objectMapper;
    private final Path dir;
    private final Path credentialFile;
    private final Path hostFile;

    public ChatGptCredentialStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.dir = ProjectPaths.data("auth", "chatgpt");
        this.credentialFile = dir.resolve("credentials.json");
        this.hostFile = dir.resolve("host-id.txt");
    }

    public synchronized Optional<ChatGptCredential> read() {
        if (!Files.exists(credentialFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    Files.readString(credentialFile, StandardCharsets.UTF_8),
                    ChatGptCredential.class));
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read ChatGPT credentials.", e);
        }
    }
    public synchronized void write(ChatGptCredential credential) {
        try {
            Files.createDirectories(dir);
            Path tmp = credentialFile.resolveSibling(
                    credentialFile.getFileName() + ".tmp");
            String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(credential);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, credentialFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to store ChatGPT credentials.", e);
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(credentialFile);
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to clear ChatGPT credentials.", e);
        }
    }

    public synchronized String hostId() {
        try {
            Files.createDirectories(dir);
            if (Files.exists(hostFile)) {
                String value = Files.readString(
                        hostFile, StandardCharsets.UTF_8).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
            String value = "urn:uuid:" + UUID.randomUUID();
            Files.writeString(
                    hostFile, value + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            return value;
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to initialize ChatGPT host id.", e);
        }
    }
}
