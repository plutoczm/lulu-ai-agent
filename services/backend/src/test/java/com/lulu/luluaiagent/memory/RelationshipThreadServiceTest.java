package com.lulu.luluaiagent.memory;

import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
