package com.lulu.luluaiagent.memory;

import cn.hutool.core.util.StrUtil;
import com.lulu.luluaiagent.model.ModelRouter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User-scoped, consent-gated long-term relationship memory.
 */
@Service
@ConditionalOnProperty(prefix = "app.memory.pgvector", name = "enabled",
        havingValue = "true")
public class RelationshipMemoryService {
    private static final String EXTRACT_PROMPT = """
            你负责从用户消息中提取适合长期保存的关系事实。
            只保留稳定、会影响后续建议的信息，例如：用户偏好、关系对象、
            当前关系阶段、重要已发生事件、明确目标、长期边界和现实约束。
            不保存整段聊天，不保存一次性的情绪波动，不推测对方内心，
            不把模型判断当事实。若没有值得长期保存的信息，只输出 NONE。
            若有，仅输出精简中文事实，最多 180 字，不要解释。
            """;

    private final VectorStore vectorStore;
    private final ChatModel memoryChatModel;
    private final JdbcTemplate jdbcTemplate;

    public RelationshipMemoryService(
            @Qualifier("relationshipMemoryVectorStore") VectorStore vectorStore,
            ModelRouter modelRouter,
            JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.memoryChatModel = modelRouter.memoryModel();
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> rememberFromMessage(String chatId, String message) {
        validateScope(chatId);
        if (StrUtil.isBlank(message)) {
            return Optional.empty();
        }
        String extracted = memoryChatModel.call(
                        new Prompt(EXTRACT_PROMPT + "\n用户消息：\n" + message))
                .getResult().getOutput().getText();
        extracted = normalizeExtraction(extracted);
        if (StrUtil.isBlank(extracted) || "NONE".equalsIgnoreCase(extracted)) {
            return Optional.empty();
        }

        String fingerprint = sha256(chatId + "\n" + extracted);
        Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relationship_memory " +
                        "WHERE metadata->>'fingerprint' = ?",
                Integer.class,
                fingerprint);
        if (duplicates != null && duplicates > 0) {
            return Optional.of(extracted);
        }

        Document document = new Document(
                extracted,
                Map.of(
                        "chat_id", chatId,
                        "kind", "relationship_memory",
                        "source", "user_message",
                        "fingerprint", fingerprint,
                        "created_at", Instant.now().toString()
                ));
        vectorStore.add(List.of(document));
        return Optional.of(extracted);
    }

    public List<Document> search(String chatId, String query, int topK) {
        validateScope(chatId);
        if (StrUtil.isBlank(query)) {
            return List.of();
        }
        Filter.Expression filter = scopeFilter(chatId);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, Math.min(topK, 10)))
                .similarityThreshold(0.25)
                .filterExpression(filter)
                .build();
        return vectorStore.similaritySearch(request);
    }

    public int count(String chatId) {
        validateScope(chatId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relationship_memory " +
                        "WHERE metadata->>'chat_id' = ?",
                Integer.class,
                chatId);
        return count == null ? 0 : count;
    }

    public void clear(String chatId) {
        validateScope(chatId);
        vectorStore.delete(scopeFilter(chatId));
    }

    public String buildContext(String chatId, String query, int topK) {
        List<Document> memories = search(chatId, query, topK);
        if (memories.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            lines.add((i + 1) + ". " + memories.get(i).getText());
        }
        return String.join("\n", lines);
    }
    private Filter.Expression scopeFilter(String chatId) {
        return new FilterExpressionBuilder()
                .eq("chat_id", chatId)
                .build();
    }

    private void validateScope(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId is required");
        }
    }

    private String normalizeExtraction(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("(?s)<think>.*?</think>", "")
                .replace("```text", "")
                .replace("```", "")
                .trim();
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500);
        }
        return cleaned;
    }
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash memory", e);
        }
    }
}
