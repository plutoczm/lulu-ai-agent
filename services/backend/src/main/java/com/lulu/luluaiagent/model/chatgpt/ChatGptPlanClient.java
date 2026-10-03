package com.lulu.luluaiagent.model.chatgpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class ChatGptPlanClient {

    private static final String API_BASE = "https://api.openai.com/v1";

    private final ChatGptOAuthService oauth;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    public ChatGptPlanClient(
            ChatGptOAuthService oauth,
            ObjectMapper objectMapper) {
        this.oauth = oauth;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<ChatGptModelInfo> listModels() {
        ChatGptCredential credential = oauth.accessCredential();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/models"))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Authorization",
                        "Bearer " + credential.accessToken())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "OpenAI model list returned HTTP "
                                + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                models = root.path("data");
            }
            List<ChatGptModelInfo> result = new ArrayList<>();
            for (JsonNode item : models) {
                String visibility =
                        item.path("visibility").asText("list");
                if (!"list".equals(visibility)) {
                    continue;
                }

                String slug = item.path("slug").asText(null);
                if (slug == null || slug.isBlank()) {
                    slug = item.path("id").asText(null);
                }
                if (slug == null || slug.isBlank()) {
                    continue;
                }

                String displayName =
                        item.path("display_name").asText(slug);
                result.add(new ChatGptModelInfo(
                        slug, displayName, visibility));
            }
            return List.copyOf(result);
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load ChatGPT plan model catalog.", e);
        }
    }

    public String complete(String model, Prompt prompt) {
        StringBuilder text = new StringBuilder();
        stream(model, prompt, text::append);
        return text.toString();
    }
    public void stream(
            String model,
            Prompt prompt,
            Consumer<String> onDelta) {
        ChatGptCredential credential = oauth.accessCredential();

        try {
            String requestBody = objectMapper.writeValueAsString(
                    buildRequest(model, prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/responses"))
                    .timeout(Duration.ofMinutes(5))
                    .header(
                            "Authorization",
                            "Bearer " + credential.accessToken())
                    .header(
                            "Content-Type",
                            "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "OpenAI Responses API returned HTTP "
                                + response.statusCode());
            }

            AtomicBoolean completed = new AtomicBoolean(false);
            try (Stream<String> lines = response.body()) {
                lines.forEach(line ->
                        handleSseLine(line, onDelta, completed));
            }
            if (!completed.get()) {
                throw new IllegalStateException(
                        "OpenAI stream ended without response.completed.");
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "ChatGPT plan inference failed.", e);
        }
    }

    private void handleSseLine(
            String line,
            Consumer<String> onDelta,
            AtomicBoolean completed) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }

        String data = line.substring(5).trim();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return;
        }

        try {
            JsonNode event = objectMapper.readTree(data);
            String type = event.path("type").asText("");

            if ("response.output_text.delta".equals(type)) {
                String delta = event.path("delta").asText("");
                if (!delta.isEmpty()) {
                    onDelta.accept(delta);
                }
                return;
            }
            if ("response.completed".equals(type)) {
                completed.set(true);
                return;
            }

            if ("response.failed".equals(type)
                    || "response.incomplete".equals(type)
                    || "error".equals(type)) {
                String code = event.path("error").path("code").asText(type);
                throw new IllegalStateException(
                        "OpenAI response failed: " + code);
            }
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse OpenAI stream event.", e);
        }
    }

    private Map<String, Object> buildRequest(
            String model,
            Prompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("stream", true);

        StringBuilder instructions = new StringBuilder();
        List<Map<String, Object>> input = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            MessageType type = message.getMessageType();

            if (type == MessageType.SYSTEM) {
                if (!instructions.isEmpty()) {
                    instructions.append("\n\n");
                }
                instructions.append(message.getText());
                continue;
            }

            if (type == MessageType.TOOL) {
                throw new UnsupportedOperationException(
                        "ChatGPT plan adapter tool messages "
                                + "are not implemented yet.");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put(
                    "role",
                    type == MessageType.ASSISTANT
                            ? "assistant"
                            : "user");
            item.put("content", message.getText());
            input.add(item);
        }

        if (!instructions.isEmpty()) {
            body.put("instructions", instructions.toString());
        }
        body.put("input", input);
        return body;
    }
}
