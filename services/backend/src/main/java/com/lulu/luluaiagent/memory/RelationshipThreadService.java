package com.lulu.luluaiagent.memory;

import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
    private final RelationshipMemoryWriteService memoryWriteService;

    public RelationshipThreadService(
            JdbcTemplate jdbcTemplate,
            RelationshipMemoryService memoryService,
            RelationshipMemoryWriteService memoryWriteService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryService = memoryService;
        this.memoryWriteService = memoryWriteService;
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
                    content_type TEXT NOT NULL DEFAULT 'text',
                    source TEXT NOT NULL DEFAULT 'coach',
                    source_key TEXT,
                    enrichment_key TEXT,
                    observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS content_type TEXT NOT NULL DEFAULT 'text'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'coach'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS source_key TEXT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS enrichment_key TEXT
                """);
        jdbcTemplate.execute("""
                ALTER TABLE relationship_thread_message
                ADD COLUMN IF NOT EXISTS observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_relationship_thread_source_key
                ON relationship_thread_message(chat_id, source_key)
                WHERE source_key IS NOT NULL AND source_key <> ''
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_relationship_thread_enrichment_key
                ON relationship_thread_message(chat_id, enrichment_key)
                WHERE enrichment_key IS NOT NULL AND enrichment_key <> ''
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
                safeMessages);
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
        memoryWriteService.rememberCoachTurn(
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

    /**
     * Incrementally stores messages observed outside the coach flow.
     *
     * Full history is retained. Context builders remain bounded separately,
     * so storage completeness does not increase prompt size.
     */
    public SyncResult syncMessages(
            String threadId,
            String personId,
            String accountId,
            String platform,
            List<SyncMessage> messages) {
        validateScope(threadId, "conversationId");
        validateScope(personId, "personId");
        validateScope(accountId, "accountId");

        if (messages == null || messages.isEmpty()) {
            return new SyncResult(0, 0, recentMessageCount(threadId));
        }

        int inserted = 0;
        int merged = 0;
        List<ConversationCoachRequest.Message> memoryCandidates =
                new ArrayList<>();
        Map<String, Integer> screenBatchCounts = new HashMap<>();
        for (SyncMessage message : messages) {
            if (message == null
                    || !StringUtils.hasText(message.text())
                    || !StringUtils.hasText(message.source())
                    || !message.source().contains("screen")) {
                continue;
            }
            String semanticKey =
                    value(message.sender()) + "\n" + message.text().trim();
            screenBatchCounts.merge(semanticKey, 1, Integer::sum);
        }

        for (SyncMessage message : messages) {
            if (message == null || !StringUtils.hasText(message.text())) {
                continue;
            }

            String sender = value(message.sender());
            String text = message.text().trim();
            String time = value(message.time());
            String contentType = StringUtils.hasText(message.contentType())
                    ? message.contentType().trim()
                    : "text";
            String source = StringUtils.hasText(message.source())
                    ? message.source().trim()
                    : "sync";
            String sourceKey = StringUtils.hasText(message.sourceKey())
                    ? message.sourceKey().trim()
                    : "";
            String replacesSourceKey =
                    StringUtils.hasText(message.replacesSourceKey())
                            ? message.replacesSourceKey().trim()
                            : "";
            Instant observedAt = parseInstant(message.observedAt());

            // A voice transcript can enrich an existing notification row.
            // The enrichment key is stored separately so a retry after an
            // ambiguous network response is idempotent without discarding
            // the original notification source key.
            if ("voice_transcript".equals(contentType)
                    && StringUtils.hasText(sourceKey)) {
                List<Long> alreadyEnriched = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM relationship_thread_message
                        WHERE chat_id = ?
                          AND enrichment_key = ?
                        LIMIT 1
                        """,
                        Long.class,
                        threadId,
                        sourceKey);
                if (!alreadyEnriched.isEmpty()) {
                    merged++;
                    continue;
                }
            }

            if ("voice_transcript".equals(contentType)
                    && StringUtils.hasText(replacesSourceKey)) {
                int enriched = jdbcTemplate.update("""
                        UPDATE relationship_thread_message
                        SET person_id = ?,
                            account_id = ?,
                            platform = ?,
                            message_text = ?,
                            message_time = ?,
                            content_type = 'voice_transcript',
                            source = CASE
                                WHEN POSITION(? IN source) > 0
                                THEN source
                                ELSE source || '+' || ?
                            END,
                            enrichment_key = NULLIF(?, ''),
                            observed_at = GREATEST(observed_at, ?)
                        WHERE chat_id = ?
                          AND sender = ?
                          AND source_key = ?
                          AND content_type = 'voice'
                        """,
                        personId,
                        accountId,
                        value(platform),
                        text,
                        time,
                        source,
                        source,
                        sourceKey,
                        Timestamp.from(observedAt),
                        threadId,
                        sender,
                        replacesSourceKey);
                if (enriched > 0) {
                    merged++;
                    memoryCandidates.add(
                            new ConversationCoachRequest.Message(
                                    sender,
                                    text,
                                    time));
                    continue;
                }
            }

            if ("voice_transcript".equals(contentType)) {
                List<Long> voicePlaceholders = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM relationship_thread_message
                        WHERE chat_id = ?
                          AND sender = ?
                          AND content_type = 'voice'
                          AND observed_at >= ?
                          AND observed_at <= ?
                        ORDER BY observed_at DESC, id DESC
                        LIMIT 2
                        """,
                        Long.class,
                        threadId,
                        sender,
                        Timestamp.from(observedAt.minusSeconds(180)),
                        Timestamp.from(observedAt.plusSeconds(180)));
                if (voicePlaceholders.size() == 1) {
                    jdbcTemplate.update("""
                            UPDATE relationship_thread_message
                            SET person_id = ?,
                                account_id = ?,
                                platform = ?,
                                message_text = ?,
                                message_time = ?,
                                content_type = 'voice_transcript',
                                source = CASE
                                    WHEN POSITION(? IN source) > 0
                                    THEN source
                                    ELSE source || '+' || ?
                                END,
                                source_key = COALESCE(
                                    NULLIF(source_key, ''),
                                    NULLIF(?, '')
                                ),
                                enrichment_key = NULLIF(?, ''),
                                observed_at = GREATEST(observed_at, ?),
                                fingerprint = ?
                            WHERE id = ?
                            """,
                            personId,
                            accountId,
                            value(platform),
                            text,
                            time,
                            source,
                            source,
                            sourceKey,
                            sourceKey,
                            Timestamp.from(observedAt),
                            sha256("voice-transcript\n" + sourceKey + "\n" + text),
                            voicePlaceholders.getFirst());
                    merged++;
                    memoryCandidates.add(
                            new ConversationCoachRequest.Message(
                                    sender,
                                    text,
                                    time));
                    continue;
                }
            }

            // OCR can recognize a slightly different set of neighboring
            // lines on consecutive captures. For a screen observation that
            // occurs only once in this batch, merge the exact sender/text
            // seen on another screen capture during a very short window.
            // If the same sender/text appears twice in this capture, keep both
            // instances distinct rather than guessing.
            String screenSemanticKey = sender + "\n" + text;
            if (source.contains("screen")
                    && screenBatchCounts.getOrDefault(
                            screenSemanticKey, 0) == 1) {
                List<Long> sameScreenMatches = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM relationship_thread_message
                        WHERE chat_id = ?
                          AND sender = ?
                          AND message_text = ?
                          AND observed_at >=
                              NOW() - INTERVAL '45 seconds'
                          AND POSITION('screen' IN source) > 0
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                        Long.class,
                        threadId,
                        sender,
                        text);
                if (!sameScreenMatches.isEmpty()) {
                    jdbcTemplate.update("""
                            UPDATE relationship_thread_message
                            SET person_id = ?,
                                account_id = ?,
                                platform = ?,
                                content_type = CASE
                                    WHEN content_type = 'unknown'
                                    THEN ?
                                    ELSE content_type
                                END,
                                source = CASE
                                    WHEN POSITION(? IN source) > 0
                                    THEN source
                                    ELSE source || '+' || ?
                                END,
                                source_key = CASE
                                    WHEN NULLIF(?, '') IS NOT NULL
                                    THEN ?
                                    ELSE source_key
                                END,
                                observed_at = GREATEST(observed_at, ?)
                            WHERE id = ?
                            """,
                            personId,
                            accountId,
                            value(platform),
                            contentType,
                            source,
                            source,
                            sourceKey,
                            sourceKey,
                            Timestamp.from(observedAt),
                            sameScreenMatches.getFirst());
                    merged++;
                    continue;
                }
            }

            // Cross-source reconciliation: notifications can arrive long
            // before the user opens the chat. Exact text/sender matches are
            // merged across notification <-> screen observations for up to
            // 24 hours. Same-screen observations are handled above.
            List<Long> recentMatches = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM relationship_thread_message
                    WHERE chat_id = ?
                      AND sender = ?
                      AND message_text = ?
                      AND observed_at >= NOW() - INTERVAL '24 hours'
                      AND (
                          (? LIKE '%screen%'
                           AND POSITION('notification' IN source) > 0)
                          OR
                          (? = 'notification'
                           AND POSITION('screen' IN source) > 0)
                      )
                      AND POSITION(? IN source) = 0
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                    Long.class,
                    threadId,
                    sender,
                    text,
                    source,
                    source,
                    source);
            if (!recentMatches.isEmpty()) {
                jdbcTemplate.update("""
                        UPDATE relationship_thread_message
                        SET person_id = ?,
                            account_id = ?,
                            platform = ?,
                            content_type = CASE
                                WHEN content_type = 'unknown'
                                THEN ?
                                ELSE content_type
                            END,
                            source = CASE
                                WHEN POSITION(? IN source) > 0
                                THEN source
                                ELSE source || '+' || ?
                            END,
                            source_key = CASE
                                WHEN (source_key IS NULL OR source_key = '')
                                THEN NULLIF(?, '')
                                ELSE source_key
                            END,
                            observed_at = GREATEST(observed_at, ?)
                        WHERE id = ?
                        """,
                        personId,
                        accountId,
                        value(platform),
                        contentType,
                        source,
                        source,
                        sourceKey,
                        Timestamp.from(observedAt),
                        recentMatches.get(0));
                merged++;
                continue;
            }

            String fingerprint = StringUtils.hasText(sourceKey)
                    ? sha256("source-key\n" + sourceKey)
                    : sha256(sender + "\n" + text + "\n" + time);
            String enrichmentKey = "voice_transcript".equals(contentType)
                    ? sourceKey
                    : "";

            int rows = jdbcTemplate.update("""
                    INSERT INTO relationship_thread_message
                        (chat_id, person_id, account_id, platform,
                         sender, message_text, message_time,
                         content_type, source, source_key, enrichment_key,
                         observed_at, fingerprint)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''),
                            NULLIF(?, ''), ?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    threadId,
                    personId,
                    accountId,
                    value(platform),
                    sender,
                    text,
                    time,
                    contentType,
                    source,
                    sourceKey,
                    enrichmentKey,
                    Timestamp.from(observedAt),
                    fingerprint);
            if (rows > 0) {
                inserted++;
                if ("text".equals(contentType)
                        || "voice_transcript".equals(contentType)) {
                    memoryCandidates.add(
                            new ConversationCoachRequest.Message(
                                    sender,
                                    text,
                                    time));
                }
            } else {
                merged++;
            }
        }

        if (!memoryCandidates.isEmpty()) {
            String memoryInput = formatForMemory(
                    platform,
                    "增量同步联系人",
                    "未提供",
                    "后台增量同步",
                    memoryCandidates);
            memoryWriteService.rememberCoachTurn(
                    personId,
                    accountId,
                    platform,
                    memoryInput);
        }

        return new SyncResult(
                inserted,
                merged,
                recentMessageCount(threadId));
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
                         sender, message_text, message_time,
                         content_type, source, observed_at, fingerprint)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'text', 'coach', NOW(), ?)
                    ON CONFLICT (chat_id, fingerprint) DO UPDATE
                    SET person_id = EXCLUDED.person_id,
                        account_id = EXCLUDED.account_id,
                        platform = EXCLUDED.platform,
                        source = CASE
                            WHEN POSITION('coach' IN relationship_thread_message.source) > 0
                            THEN relationship_thread_message.source
                            ELSE relationship_thread_message.source || '+coach'
                        END,
                        observed_at = NOW()
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

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return Instant.now();
        }
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

    public record SyncMessage(
            String sender,
            String text,
            String time,
            String contentType,
            String source,
            String sourceKey,
            String replacesSourceKey,
            String observedAt
    ) {
        public SyncMessage(
                String sender,
                String text,
                String time,
                String contentType,
                String source,
                String sourceKey,
                String observedAt) {
            this(
                    sender,
                    text,
                    time,
                    contentType,
                    source,
                    sourceKey,
                    "",
                    observedAt);
        }
    }

    public record SyncResult(
            int inserted,
            int merged,
            int totalMessages
    ) {}
}
