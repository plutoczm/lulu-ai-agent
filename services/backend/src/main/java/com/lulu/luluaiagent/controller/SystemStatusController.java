package com.lulu.luluaiagent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.auth.AuthUser;
import com.lulu.luluaiagent.config.ProjectPaths;
import com.lulu.luluaiagent.model.ModelRouter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai/system")
public class SystemStatusController {

    private final JdbcTemplate jdbcTemplate;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    @Value("${app.integrations.baidu-search.enabled:true}")
    private boolean baiduSearchEnabled;

    @Value("${app.integrations.bailian-rag.enabled:false}")
    private boolean bailianRagEnabled;

    @Value("${app.memory.pgvector.enabled:true}")
    private boolean pgvectorEnabled;

    @Value("${QQ_BOT_ENABLED:false}")
    private boolean qqBotEnabled;

    @Value("${QQ_BOT_HEALTH_PORT:8131}")
    private int qqBotHealthPort;

    public SystemStatusController(
            JdbcTemplate jdbcTemplate,
            ModelRouter modelRouter,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        AuthUser user = AuthSupport.currentUser(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseHealthy", databaseHealthy());
        result.put("relationshipMemoryCount", relationshipMemoryCount(user));
        result.put("pgvectorEnabled", pgvectorEnabled);
        result.put("bailianRagEnabled", bailianRagEnabled);
        result.put("baiduSearchEnabled", baiduSearchEnabled);
        result.put("knowledgeDocuments", knowledgeDocumentCount());
        result.put("deepSeekAvailable", modelRouter.deepSeekAvailable());
        result.put("coachPrimary", modelRouter.coachPrimaryModelName());
        result.put("agentPrimary", modelRouter.agentPrimaryModelName());
        result.put("fastModel", modelRouter.fastModelName());
        result.put("memoryModel", modelRouter.memoryModelName());
        result.put("qqBotEnabled", qqBotEnabled);
        result.put("qqBotReady", qqBotReady());
        return result;
    }

    private boolean databaseHealthy() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private long relationshipMemoryCount(AuthUser user) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM relationship_memory " +
                            "WHERE metadata->>'chat_id' LIKE ?",
                    Long.class,
                    user.id() + "__%");
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean qqBotReady() {
        if (!qqBotEnabled) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "http://127.0.0.1:" + qqBotHealthPort + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("ready").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private int knowledgeDocumentCount() {
        try {
            Path manifest = ProjectPaths.data(
                    "knowledge", "relationship-coach", "manifest.json");
            if (!Files.exists(manifest)) {
                return 0;
            }
            JsonNode root = objectMapper.readTree(Files.readString(manifest));
            JsonNode selected = root.get("selected");
            return selected != null && selected.isArray() ? selected.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
