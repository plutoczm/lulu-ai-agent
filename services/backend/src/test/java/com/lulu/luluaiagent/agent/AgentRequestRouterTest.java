package com.lulu.luluaiagent.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.lulu.luluaiagent.agent.AgentRequestRouter.Route.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRequestRouterTest {

    private final AgentRequestRouter router =
            new AgentRequestRouter(message -> Optional.of(FAST_PATH));

    @Test
    void simpleConversationUsesFastPath() {
        assertEquals(FAST_PATH, router.route("你是什么模型？"));
        assertEquals(FAST_PATH, router.route("帮我分析一下这段关系，我有点焦虑"));
        assertEquals(FAST_PATH, router.route("我现在有点焦虑，怎么办？"));
        assertEquals(FAST_PATH, router.route("给我解释一下依恋理论"));
    }

    @Test
    void explicitCurrentTimeUsesDeterministicClockWorkflow() {
        assertEquals(CURRENT_TIME, router.route("现在几点了？"));
        assertEquals(CURRENT_TIME, router.route("今天几号"));
        assertEquals(CURRENT_TIME, router.route("what time is it?"));
        assertEquals(CURRENT_TIME, router.route("now time is what?"));
    }

    @Test
    void explicitRealtimeAndOperationalRequestsUseDeterministicRoutes() {
        assertEquals(REALTIME_SEARCH, router.route("请搜索最新的 AI 新闻"));
        assertEquals(REALTIME_SEARCH, router.route("北京今天天气怎么样？"));
        assertEquals(AGENT, router.route("帮我下载这个网页里的文件"));
        assertEquals(AGENT, router.route("打开 https://example.com 看看内容"));
    }

    @Test
    void semanticClassifierHandlesUnseenNaturalLanguage() {
        AgentRequestRouter semanticRouter = new AgentRequestRouter(message -> {
            if (message.contains("钟表现在指哪儿")) {
                return Optional.of(CURRENT_TIME);
            }
            if (message.contains("眼下小米都有啥能买的")) {
                return Optional.of(REALTIME_SEARCH);
            }
            if (message.contains("帮我把桌面那个文件整理一下")) {
                return Optional.of(AGENT);
            }
            return Optional.of(FAST_PATH);
        });

        assertEquals(CURRENT_TIME, semanticRouter.route("钟表现在指哪儿了？"));
        assertEquals(REALTIME_SEARCH, semanticRouter.route("眼下小米都有啥能买的？"));
        assertEquals(AGENT, semanticRouter.route("帮我把桌面那个文件整理一下"));
        assertEquals(FAST_PATH, semanticRouter.route("给我讲讲依恋理论"));
    }

    @Test
    void classifierFailureFallsBackSafely() {
        AgentRequestRouter fallbackRouter =
                new AgentRequestRouter(message -> Optional.empty());

        assertEquals(REALTIME_SEARCH, fallbackRouter.route("小米现在有什么车？"));
        assertEquals(FAST_PATH, fallbackRouter.route("我现在有点焦虑"));
    }
}
