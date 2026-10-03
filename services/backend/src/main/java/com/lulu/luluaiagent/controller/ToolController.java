package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.tools.WebSearchTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/tools")
public class ToolController {

    private final WebSearchTool webSearchTool;

    public ToolController(WebSearchTool webSearchTool) {
        this.webSearchTool = webSearchTool;
    }

    @GetMapping("/web-search")
    public String searchWeb(String query) {
        return webSearchTool.searchWeb(query);
    }
}
