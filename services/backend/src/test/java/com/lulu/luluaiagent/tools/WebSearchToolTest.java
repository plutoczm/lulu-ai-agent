package com.lulu.luluaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSearchToolTest {

    @Test
    void missingBaiduKeyReturnsDeterministicConfigurationMessage() {
        WebSearchTool webSearchTool = new WebSearchTool("");

        assertEquals(
                "Baidu search is not configured. " +
                        "Please set BAIDU_QIANFAN_API_KEY.",
                webSearchTool.searchWeb("噜噜编程导航 codefather.cn"));
    }
}
