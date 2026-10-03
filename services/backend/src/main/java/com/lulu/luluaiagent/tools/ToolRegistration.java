package com.lulu.luluaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central tool registration.
 */
@Configuration
public class ToolRegistration {

    @Value("${app.integrations.baidu-search.enabled:true}")
    private boolean baiduSearchEnabled;

    @Value("${app.integrations.baidu-search.api-key:}")
    private String baiduSearchApiKey;

    @Value("${app.integrations.baidu-search.base-url:https://qianfan.baidubce.com/v2/ai_search/web_search}")
    private String baiduSearchBaseUrl;

    @Bean
    public WebSearchTool webSearchTool() {
        return new WebSearchTool(
                baiduSearchEnabled ? baiduSearchApiKey : "",
                baiduSearchBaseUrl
        );
    }

    @Bean
    public CurrentTimeTool currentTimeTool() {
        return new CurrentTimeTool();
    }

    @Bean
    public ToolCallback[] allTools(WebSearchTool webSearchTool, CurrentTimeTool currentTimeTool) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();

        return ToolCallbacks.from(
                fileOperationTool,
                currentTimeTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool
        );
    }
}
