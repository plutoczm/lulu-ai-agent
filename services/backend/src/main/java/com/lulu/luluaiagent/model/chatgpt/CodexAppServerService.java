package com.lulu.luluaiagent.model.chatgpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class CodexAppServerService {

    private final ChatGptOAuthService oauth;
    private final ObjectMapper objectMapper;

    @Value("${app.codex.command:codex.cmd}")
    private String codexCommand;

    @Value("${app.codex.cwd:.}")
    private String codexCwd;

    public CodexAppServerService(ChatGptOAuthService oauth, ObjectMapper objectMapper) {
        this.oauth = oauth;
        this.objectMapper = objectMapper;
    }
    public SseEmitter stream(String model, String message) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        Thread.startVirtualThread(() -> runTurn(model, message, emitter));
        return emitter;
    }

    public boolean installed() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/d", "/s", "/c",
                    codexCommand + " --version")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runTurn(String model, String message, SseEmitter emitter) {
        Process process = null;
        try {
            ChatGptCredential credential = oauth.accessCredential();
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/d", "/s", "/c",
                    codexCommand + " app-server --listen stdio://"
                            + " -c \"model_provider='openai_chatgpt_plan'\""
                            + " -c \"model_providers.openai_chatgpt_plan.name='ChatGPT plan'\""
                            + " -c \"model_providers.openai_chatgpt_plan.base_url='https://api.openai.com/v1'\""
                            + " -c \"model_providers.openai_chatgpt_plan.env_key='ACCESS_TOKEN'\""
                            + " -c \"model_providers.openai_chatgpt_plan.wire_api='responses'\""
                            + " -c \"model_providers.openai_chatgpt_plan.requires_openai_auth=false\""
                            + " -c \"model_providers.openai_chatgpt_plan.supports_websockets=false\"");
            pb.directory(Path.of(codexCwd).toAbsolutePath().normalize().toFile());
            pb.environment().put("ACCESS_TOKEN", credential.accessToken());
            process = pb.start();
            Process child = process;
            Thread.startVirtualThread(() -> drainErrors(child));

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         process.getInputStream(), StandardCharsets.UTF_8))) {

                send(writer, Map.of(
                        "id", 1,
                        "method", "initialize",
                        "params", Map.of("clientInfo", Map.of(
                                "name", "lulu_ai",
                                "title", "噜噜",
                                "version", "0.1.0"))));
                JsonNode init = awaitResponse(reader, 1);
                failOnRpcError(init, "Codex initialize failed");
                send(writer, Map.of("method", "initialized", "params", Map.of()));

                send(writer, Map.of(
                        "id", 2,
                        "method", "thread/start",
                        "params", Map.of(
                                "model", model,
                                "cwd", Path.of(codexCwd).toAbsolutePath().normalize().toString(),
                                "approvalPolicy", "on-request",
                                "sandbox", "workspace-write")));
                JsonNode threadResponse = awaitResponse(reader, 2);
                failOnRpcError(threadResponse, "Codex thread/start failed");
                String threadId = threadResponse.path("result").path("thread").path("id").asText();
                if (threadId.isBlank()) {
                    throw new IllegalStateException("Codex did not return a thread id.");
                }
                send(writer, Map.of(
                        "id", 3,
                        "method", "turn/start",
                        "params", Map.of(
                                "threadId", threadId,
                                "input", java.util.List.of(Map.of(
                                        "type", "text",
                                        "text", message)))));

                boolean completed = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode event = objectMapper.readTree(line);

                    if (event.has("id") && event.path("id").asInt(-1) == 3) {
                        failOnRpcError(event, "Codex turn/start failed");
                        continue;
                    }
                    String method = event.path("method").asText("");
                    JsonNode params = event.path("params");
                    if ("item/agentMessage/delta".equals(method)) {
                        String delta = params.path("delta").asText("");
                        if (!delta.isEmpty()) safeSend(emitter, delta);
                    } else if ("turn/completed".equals(method)) {
                        String status = params.path("turn").path("status").asText("");
                        if (!"completed".equals(status)) {
                            throw new IllegalStateException("Codex turn ended with status: " + status);
                        }
                        completed = true;
                        break;
                    }
                }
                if (!completed) throw new IllegalStateException("Codex stream ended before turn/completed.");
            }

            safeSend(emitter, "[DONE]");
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
    private JsonNode awaitResponse(BufferedReader reader, int id) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            JsonNode node = objectMapper.readTree(line);
            if (node.has("id") && node.path("id").asInt(-1) == id) return node;
        }
        throw new EOFException("Codex app-server exited before response id=" + id);
    }

    private void failOnRpcError(JsonNode node, String prefix) {
        if (node.hasNonNull("error")) {
            throw new IllegalStateException(prefix + ": " + node.path("error").toString());
        }
    }

    private void send(BufferedWriter writer, Object payload) throws Exception {
        writer.write(objectMapper.writeValueAsString(payload));
        writer.newLine();
        writer.flush();
    }

    private void drainErrors(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // Avoid blocking if Codex emits diagnostics on stderr.
            }
        } catch (IOException ignored) {
        }
    }

    private void safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(data);
        } catch (IOException ignored) {
        }
    }
}
