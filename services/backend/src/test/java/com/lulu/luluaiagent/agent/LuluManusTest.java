package com.lulu.luluaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LuluManusTest {

    @Test
    void constructorConfiguresBoundedToolAgentWithoutLiveModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        LuluManus luluManus = new LuluManus(
                new ToolCallback[0],
                chatModel);

        assertEquals("luluManus", luluManus.getName());
        assertEquals(8, luluManus.getMaxSteps());
        assertNotNull(luluManus.getChatClient());
        assertTrue(luluManus.getSystemPrompt().contains(
                LocalDate.now().toString()));
        assertTrue(luluManus.getSystemPrompt().contains(
                "只有在任务确实需要外部信息"));
        assertTrue(luluManus.getNextStepPrompt().contains(
                "选择最少必要工具"));
    }
}
