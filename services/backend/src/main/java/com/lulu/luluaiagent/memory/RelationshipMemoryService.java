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
    private static final int HISTORY_CHUNK_CHARS = 5_000;
    private static final int MAX_HISTORY_CHARS = 40_000;

    private static final String EXTRACT_PROMPT = """
            你负责从用户消息中提取适合长期保存的关系事实。
            只保留稳定、会影响后续建议的信息，例如：用户偏好、关系对象、
            当前关系阶段、重要已发生事件、明确目标、长期边界和现实约束。
            不保存整段聊天，不保存一次性的情绪波动，不推测对方内心，
            不把模型判断当事实。若没有值得长期保存的信息，只输出 NONE。
            若有，仅输出精简中文事实，最多 180 字，不要解释。
            """;

    private static final String HISTORY_EXTRACT_PROMPT = """
            你负责从一段用户主动导入的历史聊天中提取长期关系记忆。
            只提取聊天原文能够支持的稳定事实，不读心、不猜测动机。
            可提取：双方明确偏好、关系阶段、重要已发生事件、长期边界、
            现实约束、反复出现的沟通模式、用户明确表达的目标。
            不保存逐条聊天原文，不把一次性情绪当长期事实。
            最多输出 8 条，每条一行、每条不超过 100 个中文字符。
            不要编号，不要 Markdown。没有可保存事实时只输出 NONE。
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
        return rememberFromMessage(chatId, chatId, "unknown", message);
    }

    public Optional<String> rememberFromMessage(
            String personId,
            String sourceAccountId,
            String sourcePlatform,
            String message) {
        validateScope(personId);
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

        persistFact(
                personId,
                extracted,
                "coach_turn",
                sourceAccountId,
                sourcePlatform);
        return Optional.of(extracted);
    }

    /**
     * Extract compact long-term facts from a user-approved history import.
     * Only compact facts are persisted in the vector store.
     */
    public int rememberFromHistory(String chatId, String historyText) {
        return rememberFromHistory(chatId, chatId, "unknown", historyText);
    }

    public int rememberFromHistory(
            String personId,
            String sourceAccountId,
            String sourcePlatform,
            String historyText) {
        validateScope(personId);
        if (StrUtil.isBlank(historyText)) {
            return 0;
        }

        String bounded = historyText.length() <= MAX_HISTORY_CHARS
                ? historyText
                : historyText.substring(historyText.length() - MAX_HISTORY_CHARS);

        int saved = 0;
        for (String chunk : chunk(bounded, HISTORY_CHUNK_CHARS)) {
            String raw = memoryChatModel.call(
                            new Prompt(HISTORY_EXTRACT_PROMPT + "\n历史聊天：\n" + chunk))
                    .getResult().getOutput().getText();
            for (String fact : parseFacts(raw)) {
                if (persistFact(
                        personId,
                        fact,
                        "history_import",
                        sourceAccountId,
                        sourcePlatform)) {
                    saved++;
                }
            }
        }
        return saved;
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

    private boolean persistFact(
            String personId,
            String fact,
            String source,
            String sourceAccountId,
            String sourcePlatform) {
        String normalized = normalizeExtraction(fact);
        if (StrUtil.isBlank(normalized) || "NONE".equalsIgnoreCase(normalized)) {
            return false;
        }

        String fingerprint = sha256(personId + "\n" + normalized);
        Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relationship_memory " +
                        "WHERE metadata->>'fingerprint' = ?",
                Integer.class,
                fingerprint);
        if (duplicates != null && duplicates > 0) {
            return false;
        }

        Document document = new Document(
                normalized,
                Map.of(
                        "chat_id", personId,
                        "person_id", personId,
                        "kind", "relationship_memory",
                        "source", source,
                        "source_account_id",
                        StrUtil.isBlank(sourceAccountId) ? "unknown" : sourceAccountId,
                        "source_platform",
                        StrUtil.isBlank(sourcePlatform) ? "unknown" : sourcePlatform,
                        "fingerprint", fingerprint,
                        "created_at", Instant.now().toString()
                ));
        vectorStore.add(List.of(document));
        return true;
    }

    public int reassignSourceAccount(
            String oldPersonId,
            String newPersonId,
            String sourceAccountId) {
        validateScope(oldPersonId);
        validateScope(newPersonId);
        if (StrUtil.isBlank(sourceAccountId)
                || oldPersonId.equals(newPersonId)) {
            return 0;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, content
                FROM relationship_memory
                WHERE metadata->>'chat_id' = ?
                  AND metadata->>'source_account_id' = ?
                """,
                oldPersonId,
                sourceAccountId);

        int moved = 0;
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String content = String.valueOf(row.get("content"));
            String newFingerprint = sha256(newPersonId + "\n" + content);

            Integer duplicate = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM relationship_memory
                    WHERE metadata->>'chat_id' = ?
                      AND metadata->>'fingerprint' = ?
                    """,
                    Integer.class,
                    newPersonId,
                    newFingerprint);

            if (duplicate != null && duplicate > 0) {
                jdbcTemplate.update(
                        "DELETE FROM relationship_memory WHERE id = ?",
                        id);
                continue;
            }

            jdbcTemplate.update(
                    """
                    UPDATE relationship_memory
                    SET metadata = (
                        jsonb_set(
                            jsonb_set(
                                jsonb_set(
                                    metadata::jsonb,
                                    '{chat_id}',
                                    to_jsonb(CAST(? AS text)),
                                    true
                                ),
                                '{person_id}',
                                to_jsonb(CAST(? AS text)),
                                true
                            ),
                            '{fingerprint}',
                            to_jsonb(CAST(? AS text)),
                            true
                        )
                    )::json
                    WHERE id = ?
                    """,
                    newPersonId,
                    newPersonId,
                    newFingerprint,
                    id);
            moved++;
        }
        return moved;
    }

    private List<String> parseFacts(String text) {
        String cleaned = normalizeExtraction(text);
        if (StrUtil.isBlank(cleaned) || "NONE".equalsIgnoreCase(cleaned)) {
            return List.of();
        }
        return cleaned.lines()
                .map(String::trim)
                .map(line -> line.replaceFirst("^[\\-•*\\d.、)）\\s]+", "").trim())
                .filter(StrUtil::isNotBlank)
                .filter(line -> !"NONE".equalsIgnoreCase(line))
                .limit(8)
                .toList();
    }

    private List<String> chunk(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                if (newline > start + maxChars / 2) {
                    end = newline + 1;
                }
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
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
        if (cleaned.length() > 2_000) {
            cleaned = cleaned.substring(0, 2_000);
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
