package com.lulu.luluaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Web search tool backed by Baidu Qianfan official Baidu Search API.
 */
public class WebSearchTool {

    private static final String DEFAULT_BASE_URL =
            "https://qianfan.baidubce.com/v2/ai_search/web_search";

    private final String apiKey;
    private final String baseUrl;

    public WebSearchTool(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public WebSearchTool(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Tool(description = "Search current web information using Baidu Search")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        if (StrUtil.isBlank(apiKey)) {
            return "Baidu search is not configured. Please set BAIDU_QIANFAN_API_KEY.";
        }

        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", query);

        JSONArray messages = new JSONArray();
        messages.add(message);

        JSONObject webResource = new JSONObject();
        webResource.set("type", "web");
        webResource.set("top_k", 10);

        JSONArray resourceTypes = new JSONArray();
        resourceTypes.add(webResource);

        JSONObject payload = new JSONObject();
        payload.set("messages", messages);
        payload.set("search_source", "baidu_search_v2");
        payload.set("resource_type_filter", resourceTypes);

        JSONObject sort = new JSONObject();
        sort.set("priority", "auto");
        payload.set("sort", sort);
        payload.set("safe_search", true);

        String bearerToken = "Bearer " + apiKey;
        try (HttpResponse response = HttpRequest.post(baseUrl)
                .header("Authorization", bearerToken)
                .header("X-Appbuilder-Authorization", bearerToken)
                .header("Content-Type", "application/json")
                .body(payload.toString())
                .timeout(30000)
                .execute()) {
            String body = response.body();
            if (!response.isOk()) {
                return "Baidu search failed: HTTP " + response.getStatus();
            }

            JSONObject result = JSONUtil.parseObj(body);
            if (result.containsKey("code")) {
                return "Baidu search failed: "
                        + result.getStr("message", result.getStr("code"));
            }

            JSONArray references = result.getJSONArray("references");
            if (references == null || references.isEmpty()) {
                return "No Baidu search results found.";
            }

            StringBuilder output = new StringBuilder();
            int limit = Math.min(5, references.size());
            for (int i = 0; i < limit; i++) {
                JSONObject ref = references.getJSONObject(i);
                String content = ref.getStr("content", ref.getStr("snippet", ""));
                if (content.length() > 600) {
                    content = content.substring(0, 600) + "...";
                }
                output.append(i + 1).append(". ")
                        .append(ref.getStr("title", "Untitled")).append("\n")
                        .append(ref.getStr("url", "")).append("\n")
                        .append(content).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            return "Baidu search failed: " + e.getMessage();
        }
    }
}
