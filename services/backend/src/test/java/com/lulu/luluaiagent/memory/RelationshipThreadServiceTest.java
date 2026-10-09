package com.lulu.luluaiagent.memory;

import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelationshipThreadServiceTest {

    @Test
    void tailMessagesKeepsOnlyMostRecentFifty() {
        List<ConversationCoachRequest.Message> messages = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            messages.add(new ConversationCoachRequest.Message(
                    i % 2 == 0 ? "我" : "对方",
                    "message-" + i,
                    Integer.toString(i)));
        }

        List<ConversationCoachRequest.Message> tail =
                RelationshipThreadService.tailMessages(messages, 50);

        assertEquals(50, tail.size());
        assertEquals("message-11", tail.getFirst().text());
        assertEquals("message-60", tail.getLast().text());
    }

    @Test
    void historyMemoryInputContainsOnlyExplicitMetadataAndMessages() {
        List<ConversationCoachRequest.Message> messages = List.of(
                new ConversationCoachRequest.Message("我", "周末有空吗", "1"),
                new ConversationCoachRequest.Message("对方", "周六可以", "2"));

        String formatted = RelationshipThreadService.formatForMemory(
                "wechat",
                "小王",
                "暧昧",
                "自然推进",
                messages);

        assertTrue(formatted.contains("平台：wechat"));
        assertTrue(formatted.contains("对方：小王"));
        assertTrue(formatted.contains("关系阶段：暧昧"));
        assertTrue(formatted.contains("用户目标：自然推进"));
        assertTrue(formatted.contains("我：周末有空吗"));
        assertTrue(formatted.contains("对方：周六可以"));
        assertFalse(formatted.contains("模型"));
    }

    @Test
    void personAccountAndThreadScopesRemainDistinct() {
        ConversationCoachRequest request = new ConversationCoachRequest(
                "wechat",
                "thread_wechat_big",
                "person_xiaowang",
                "account_wechat_big",
                "我",
                "小王大号",
                "暧昧",
                "自然推进",
                "短句",
                List.of(new ConversationCoachRequest.Message(
                        "对方",
                        "今天有点忙",
                        "1")));

        assertEquals("thread_wechat_big", request.conversationId());
        assertEquals("person_xiaowang", request.personId());
        assertEquals("account_wechat_big", request.accountId());
    }

    @Test
    void emptyTailIsSafe() {
        assertTrue(RelationshipThreadService
                .tailMessages(List.of(), 50)
                .isEmpty());
        assertTrue(RelationshipThreadService
                .tailMessages(null, 50)
                .isEmpty());
    }

    @Test
    void syncInsertedTextSchedulesPersonMemory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "今天终于忙完了",
                                "2026-10-09T12:00:00Z",
                                "text",
                                "notification",
                                "notification:test-1",
                                "2026-10-09T12:00:00Z")));

        assertEquals(1, result.inserted());
        assertEquals(0, result.merged());
        verify(writer).rememberCoachTurn(
                eq("person_xiaowang"),
                eq("account_wechat"),
                eq("wechat"),
                contains("今天终于忙完了"));
    }

    @Test
    void syncSecondSourceMergesWithoutDuplicateMemoryExtraction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of(42L));
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "今天终于忙完了",
                                "2026-10-09T12:05:00Z",
                                "text",
                                "passive_screen_ocr",
                                "screen:v2:test-1",
                                "2026-10-09T12:05:00Z")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.merged());
        verifyNoInteractions(writer);
    }

    @Test
    void repeatedScreenObservationWithinShortWindowMerges() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                contains("INTERVAL '45 seconds'"),
                eq(Long.class),
                eq("thread_wechat"),
                eq("对方"),
                eq("今天终于忙完了")))
                .thenReturn(List.of(77L));
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "今天终于忙完了",
                                "2026-10-09T12:05:10Z",
                                "text",
                                "passive_screen_ocr",
                                "screen:v3:test-1",
                                "2026-10-09T12:05:10Z")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.merged());
        verifyNoInteractions(writer);
    }

    @Test
    void identicalMessagesVisibleTwiceAreNotFuzzyMergedTogether() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                contains("INTERVAL '45 seconds'"),
                eq(Long.class),
                any(),
                any(),
                any()))
                .thenReturn(List.of(77L));
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(2);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(
                                new RelationshipThreadService.SyncMessage(
                                        "对方",
                                        "哈哈",
                                        "2026-10-09T12:05:10Z",
                                        "text",
                                        "passive_screen_ocr",
                                        "screen:v3:first",
                                        "2026-10-09T12:05:10Z"),
                                new RelationshipThreadService.SyncMessage(
                                        "对方",
                                        "哈哈",
                                        "2026-10-09T12:05:11Z",
                                        "text",
                                        "passive_screen_ocr",
                                        "screen:v3:second",
                                        "2026-10-09T12:05:11Z")));

        assertEquals(2, result.inserted());
        assertEquals(0, result.merged());
        verify(jdbc, never()).queryForList(
                contains("INTERVAL '45 seconds'"),
                eq(Long.class),
                any(),
                any(),
                any());
    }

    @Test
    void voiceTranscriptUpgradesSingleNearbyPlaceholder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of(7L));
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "我刚下班，你呢？",
                                "2026-10-09T12:01:00Z",
                                "voice_transcript",
                                "root_voice_asr",
                                "voice-root:abc",
                                "2026-10-09T12:01:00Z")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.merged());
        verify(writer).rememberCoachTurn(
                eq("person_xiaowang"),
                eq("account_wechat"),
                eq("wechat"),
                contains("我刚下班，你呢？"));
    }

    @Test
    void voiceTranscriptPrefersExplicitNotificationSourceKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.update(
                contains("source_key = ?"),
                any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "我刚下班，你呢？",
                                "2026-10-09T12:01:00Z",
                                "voice_transcript",
                                "root_voice_asr",
                                "voice-root:abc",
                                "notification:wechat-voice-1",
                                "2026-10-09T12:01:00Z")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.merged());
        verify(jdbc, never()).queryForList(
                anyString(),
                eq(Long.class),
                any(),
                any(),
                any(),
                any());
        verify(writer).rememberCoachTurn(
                eq("person_xiaowang"),
                eq("account_wechat"),
                eq("wechat"),
                contains("我刚下班，你呢？"));
    }

    @Test
    void voiceTranscriptRetryAfterLostResponseIsIdempotent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RelationshipMemoryService memory = mock(RelationshipMemoryService.class);
        RelationshipMemoryWriteService writer =
                mock(RelationshipMemoryWriteService.class);
        RelationshipThreadService service =
                new RelationshipThreadService(jdbc, memory, writer);

        when(jdbc.queryForList(
                contains("enrichment_key = ?"),
                eq(Long.class),
                eq("thread_wechat"),
                eq("voice-root:abc")))
                .thenReturn(List.of(99L));
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                any()))
                .thenReturn(1);

        RelationshipThreadService.SyncResult result =
                service.syncMessages(
                        "thread_wechat",
                        "person_xiaowang",
                        "account_wechat",
                        "wechat",
                        List.of(new RelationshipThreadService.SyncMessage(
                                "对方",
                                "我刚下班，你呢？",
                                "2026-10-09T12:01:00Z",
                                "voice_transcript",
                                "root_voice_asr",
                                "voice-root:abc",
                                "notification:wechat-voice-1",
                                "2026-10-09T12:01:00Z")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.merged());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(writer);
    }
}
