package com.lulu.luluaiagent.memory;

import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Account-scoped recent-message storage with person-scoped long-term memory.
 */
@Service
@ConditionalOnProperty(prefix = "app.memory.pgvector", name = "enabled",
        havingValue = "true")
public class RelationshipThreadService {
    public static final int MAX_RECENT_MESSAGES = 50;

    private final JdbcTemplate jdbcTemplate;
    private final RelationshipMemoryService memoryService;

    public RelationshipThreadService(
            JdbcTemplate jdbcTemplate,
            RelationshipMemoryService memoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryService = memoryService;
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS relationship_thread_message (
                    id BIGSERIAL PRIMARY KEY,
                    chat_id TEXT NOT NULL,
                    person_id TEXT,
                    account_id TEXT,
                    platform TEXT,
                    sender TEXT NOT NULL,
                    message_text TEXT NOT NULL,
                    message_time TEXT,
                    fingerprint TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS person_id TEXT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS account_id TEXT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS platform TEXT
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_relationship_thread_message
                ON relationship_thread_message(chat_id, fingerprint)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ix_relationship_thread_message_chat_id
                ON relationship_thread_message(chat_id, id DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ix_relationship_thread_message_person
                ON relationship_thread_message(person_id, account_id, id DESC)
                """);
    }

    public ThreadStatus importHistory(
            String threadId,
            String personId,
            String accountId,
            String platform,
            String otherAlias,
            String relationshipStage,
            List<ConversationCoachRequest.Message> messages) {
        validateScope(threadId, "conversationId");
        validateScope(personId, "personId");
        validateScope(accountId, "accountId");

        List<ConversationCoachRequest.Message> safeMessages =
                messages == null ? List.of() : messages;

        String history = formatForMemory(
                platform,
                otherAlias,
                relationshipStage,
                "历史首次导入",
                safeMessages);
        int importedFacts = memoryService.rememberFromHistory(
                personId,
                accountId,
                platform,
                history);

        appendMessages(
                threadId,
                personId,
                accountId,
                platform,
                tailMessages(safeMessages, MAX_RECENT_MESSAGES));
        return new ThreadStatus(
                recentMessageCount(threadId),
                memoryService.count(personId),
                importedFacts);
    }

    public void recordCoachTurn(
            String threadId,
            String personId,
            String accountId,
            ConversationCoachRequest request) {
        validateScope(threadId, "conversationId");
        validateScope(personId, "personId");
        validateScope(accountId, "accountId");
        if (request == null || request.messages() == null) {
            return;
        }

        appendMessages(
                threadId,
                personId,
                accountId,
                request.platform(),
                request.messages());

        String memoryInput = formatForMemory(
                request.platform(),
                request.otherAlias(),
                request.relationshipStage(),
                request.goal(),
                request.messages());
        memoryService.rememberFromMessage(
                personId,
                accountId,
                request.platform(),
                memoryInput);
    }

    public String buildRecentContext(String threadId, int limit) {
        List<ConversationCoachRequest.Message> recent =
                recentMessages(threadId, limit);
        if (recent.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ConversationCoachRequest.Message message : recent) {
            builder.append('[')
                    .append(value(message.time()))
                    .append("] ")
                    .append(value(message.sender()))
                    .append('：')
                    .append(value(message.text()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    public List<ConversationCoachRequest.Message> recentMessages(
            String threadId,
            int limit) {
        validateScope(threadId, "conversationId");
        int bounded = Math.max(1, Math.min(limit, MAX_RECENT_MESSAGES));
        return jdbcTemplate.query("""
                        SELECT sender, message_text, message_time
                        FROM (
                            SELECT id, sender, message_text, message_time
                            FROM relationship_thread_message
                            WHERE chat_id = ?
                            ORDER BY id DESC
                            LIMIT ?
                        ) recent
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> new ConversationCoachRequest.Message(
                        rs.getString("sender"),
                        rs.getString("message_text"),
                        rs.getString("message_time")),
                threadId,
                bounded);
    }

    public ThreadStatus status(String threadId, String personId) {
        validateScope(threadId, "conversationId");
        validateScope(personId, "personId");
        return new ThreadStatus(
                recentMessageCount(threadId),
                memoryService.count(personId),
                0);
    }

    public ThreadStatus clearAccount(
            String threadId,
            String personId) {
        validateScope(threadId, "conversationId");
        validateScope(personId, "personId");
        jdbcTemplate.update(
                "DELETE FROM relationship_thread_message WHERE chat_id = ?",
                threadId);
        return new ThreadStatus(
                0,
                memoryService.count(personId),
                0);
    }

    public ThreadStatus clearPersonMemory(
            String threadId,
            String personId) {
        validateScope(personId, "personId");
        memoryService.clear(personId);
        return new ThreadStatus(
                StringUtils.hasText(threadId)
                        ? recentMessageCount(threadId)
                        : 0,
                0,
                0);
    }

    public ReassignResult reassignAccount(
            String threadId,
            String oldPersonId,
            String newPersonId,
            String accountId) {
        validateScope(threadId, "conversationId");
        validateScope(oldPersonId, "oldPersonId");
        validateScope(newPersonId, "newPersonId");
        validateScope(accountId, "accountId");

        jdbcTemplate.update(
                """
                UPDATE relationship_thread_message
                SET person_id = ?
                WHERE chat_id = ?
                  AND account_id = ?
                  AND (person_id = ? OR person_id IS NULL)
                """,
                newPersonId,
                threadId,
                accountId,
                oldPersonId);

        int movedFacts = memoryService.reassignSourceAccount(
                oldPersonId,
                newPersonId,
                accountId);

        return new ReassignResult(
                movedFacts,
                recentMessageCount(threadId),
                memoryService.count(newPersonId));
    }

    private void appendMessages(
            String threadId,
            String personId,
            String accountId,
            String platform,
            List<ConversationCoachRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ConversationCoachRequest.Message message : messages) {
            if (message == null || !StringUtils.hasText(message.text())) {
                continue;
            }
            String sender = value(message.sender());
            String text = message.text().trim();
            String time = value(message.time());
            String fingerprint = sha256(sender + "\n" + text + "\n" + time);
            jdbcTemplate.update("""
                    INSERT INTO relationship_thread_message
                        (chat_id, person_id, account_id, platform,
                         sender, message_text, message_time, fingerprint)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (chat_id, fingerprint) DO UPDATE
                    SET person_id = EXCLUDED.person_id,
                        account_id = EXCLUDED.account_id,
                        platform = EXCLUDED.platform
                    """,
                    threadId,
                    personId,
                    accountId,
                    value(platform),
                    sender,
                    text,
                    time,
                    fingerprint);
        }

        jdbcTemplate.update("""
                DELETE FROM relationship_thread_message
                WHERE chat_id = ?
                  AND id NOT IN (
                      SELECT id
                      FROM relationship_thread_message
                      WHERE chat_id = ?
                      ORDER BY id DESC
                      LIMIT ?
                  )
                """,
                threadId,
                threadId,
                MAX_RECENT_MESSAGES);
    }

    private int recentMessageCount(String threadId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relationship_thread_message WHERE chat_id = ?",
                Integer.class,
                threadId);
        return count == null ? 0 : count;
    }

    static List<ConversationCoachRequest.Message> tailMessages(
            List<ConversationCoachRequest.Message> messages,
            int limit) {
        if (messages == null || messages.isEmpty() || limit <= 0) {
            return List.of();
        }
        int from = Math.max(0, messages.size() - limit);
        return List.copyOf(messages.subList(from, messages.size()));
    }

    static String formatForMemory(
            String platform,
            String otherAlias,
            String relationshipStage,
            String goal,
            List<ConversationCoachRequest.Message> messages) {
        List<String> lines = new ArrayList<>();
        lines.add("平台：" + value(platform));
        lines.add("对方：" + value(otherAlias));
        lines.add("关系阶段：" + value(relationshipStage));
        lines.add("用户目标：" + value(goal));
        lines.add("聊天原文：");
        if (messages != null) {
            for (ConversationCoachRequest.Message message : messages) {
                if (message == null || !StringUtils.hasText(message.text())) {
                    continue;
                }
                lines.add(value(message.sender()) + "：" + message.text().trim());
            }
        }
        return String.join("\n", lines);
    }

    private static String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "未提供";
    }

    private void validateScope(String id, String name) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash thread message", e);
        }
    }

    public record ThreadStatus(
            int recentMessages,
            int memoryFacts,
            int importedFacts
    ) {}

    public record ReassignResult(
            int movedMemoryFacts,
            int recentMessages,
            int memoryFacts
    ) {}
}
