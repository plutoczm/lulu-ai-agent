package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.auth.AuthUser;
import com.lulu.luluaiagent.config.ProjectPaths;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/ai/search")
public class SearchController {

    private static final List<SearchEntry> FEATURES = List.of(
            new SearchEntry("app", "AI对话军师", "A/B/C 三档回复建议与一键复制", "/chat-coach",
                    "对话 聊天 回复 微信 qq 军师 coach"),
            new SearchEntry("app", "AI超级智能体", "搜索、网页、文件与多工具任务", "/super-agent",
                    "智能体 agent 搜索 文件 网页 工具"),
            new SearchEntry("feature", "知识库", "浏览关系沟通知识与实战指南", "/knowledge",
                    "知识库 rag 百炼 关系 沟通 恋爱"),
            new SearchEntry("feature", "系统状态", "查看数据库、模型、知识库与 QQ Bot 状态", "/status",
                    "状态 health pgvector ollama qq bot"),
            new SearchEntry("admin", "模型配置", "ChatGPT 登录、模型目录与路由切换", "/models",
                    "模型 chatgpt codex deepseek qwen 路由 配置"),
            new SearchEntry("feature", "QQ 官方机器人", "查看 QQ Bot 运行状态与渠道接入", "/status",
                    "qq 机器人 bot 渠道 websocket"),
            new SearchEntry("feature", "Android 手机助手", "微信与 QQ 悬浮 A/B/C 回复助手", "/knowledge",
                    "android 微信 qq 手机 悬浮 复制")
    );

    @GetMapping
    public SearchResponse search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(defaultValue = "all") String scope,
            HttpServletRequest request) {
        AuthUser user = AuthSupport.currentUser(request);
        String query = normalize(q);
        int boundedLimit = Math.max(1, Math.min(limit, 30));

        List<SearchResult> results = new ArrayList<>();
        boolean knowledgeOnly = "knowledge".equalsIgnoreCase(scope);
        if (!knowledgeOnly) {
        for (SearchEntry entry : FEATURES) {
            if ("admin".equals(entry.type()) && !user.admin()) {
                continue;
            }
            int score = score(entry.title() + " " + entry.subtitle() + " " + entry.keywords(), query);
            if (query.isBlank() || score > 0) {
                results.add(new SearchResult(
                        "admin".equals(entry.type()) ? "feature" : entry.type(),
                        entry.title(), entry.subtitle(), entry.path(), null, score + 100));
            }
        }
        }

        results.addAll(searchKnowledge(query, knowledgeOnly));
        results.sort(Comparator
                .comparingInt(SearchResult::score).reversed()
                .thenComparing(SearchResult::title));

        return new SearchResponse(
                results.stream().limit(boundedLimit).toList(),
                query,
                results.size());
    }

    private List<SearchResult> searchKnowledge(String query, boolean includeAll) {
        if (query.isBlank() && !includeAll) {
            return List.of();
        }
        Path knowledgeDir = ProjectPaths.data("knowledge", "relationship-coach");
        if (!Files.isDirectory(knowledgeDir)) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        try (var stream = Files.list(knowledgeDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .forEach(path -> addKnowledgeResult(path, query, includeAll, results));
        } catch (Exception ignored) {
            return List.of();
        }
        return results;
    }

    private void addKnowledgeResult(Path path, String query, boolean includeAll, List<SearchResult> results) {
        try {
            String filename = path.getFileName().toString();
            String title = cleanTitle(filename);
            String content = Files.readString(path);
            int titleScore = score(title, query) * 4;
            int contentScore = score(content, query);
            int total = titleScore + contentScore;
            if (includeAll && query.isBlank()) {
                total = 1;
            }
            if (total <= 0) {
                return;
            }
            results.add(new SearchResult(
                    "knowledge",
                    title,
                    snippet(content, query),
                    "/knowledge?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
                    filename,
                    total));
        } catch (Exception ignored) {
        }
    }

    private String cleanTitle(String filename) {
        String value = filename.replaceFirst("\\.md$", "");
        value = value.replaceFirst("^(knowledge|practical)-", "");
        value = value.replaceFirst("^\\d{2}-", "");
        return value;
    }

    private String snippet(String content, String query) {
        String flat = content.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        String lower = flat.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(query);
        int start = index >= 0 ? Math.max(0, index - 55) : 0;
        int end = Math.min(flat.length(), start + 150);
        String text = flat.substring(start, end).trim();
        return (start > 0 ? "…" : "") + text + (end < flat.length() ? "…" : "");
    }

    private int score(String text, String query) {
        if (query.isBlank()) {
            return 1;
        }
        String haystack = normalize(text);
        if (haystack.isBlank()) {
            return 0;
        }
        if (haystack.equals(query)) {
            return 100;
        }
        if (haystack.startsWith(query)) {
            return 60;
        }
        int score = 0;
        for (String token : query.split("\\s+")) {
            if (token.isBlank()) continue;
            int first = haystack.indexOf(token);
            if (first >= 0) {
                score += 15;
                int count = 1;
                int next = haystack.indexOf(token, first + token.length());
                while (next >= 0 && count < 4) {
                    score += 3;
                    count++;
                    next = haystack.indexOf(token, next + token.length());
                }
            }
        }
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record SearchEntry(
            String type,
            String title,
            String subtitle,
            String path,
            String keywords) {
    }

    public record SearchResult(
            String type,
            String title,
            String subtitle,
            String path,
            String document,
            int score) {
    }

    public record SearchResponse(
            List<SearchResult> results,
            String query,
            int total) {
    }
}
