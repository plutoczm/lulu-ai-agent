package com.lulu.luluaiagent.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hybrid request router.
 *
 * <p>High-confidence rules handle explicit commands cheaply. Natural-language
 * requests that are not obvious are classified semantically. The classifier
 * selects only a fixed route; execution remains deterministic.</p>
 */
@Component
public class AgentRequestRouter {

    public enum Route {
        FAST_PATH,
        CURRENT_TIME,
        REALTIME_SEARCH,
        AGENT
    }

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private static final List<String> EXPLICIT_TIME_INTENTS = List.of(
            "几点", "现在时间", "当前时间", "北京时间", "时间是多少",
            "今天几号", "今天日期", "当前日期", "今天星期", "星期几", "周几",
            "what time is it", "what's the time", "what is the time",
            "current time", "time now", "now time", "what time now",
            "today's date", "todays date", "what date is it", "current date",
            "what day is it"
    );

    private static final List<String> EXPLICIT_REALTIME_INTENTS = List.of(
            "搜索", "搜一下", "查一下", "查找", "联网",
            "最新", "实时", "新闻", "股价", "汇率", "报价", "天气", "比赛结果",
            "search the web", "look up", "latest", "real-time", "realtime",
            "breaking news", "stock price", "exchange rate", "weather"
    );

    private static final List<String> FRESHNESS_MARKERS = List.of(
            "目前", "当前", "现在", "今天", "今日", "最近", "这周", "本周",
            "currently", "right now", "today", "recent", "recently", "this week"
    );

    private static final List<String> DYNAMIC_SUBJECTS = List.of(
            "车", "车型", "汽车", "手机", "产品", "型号", "版本",
            "价格", "政策", "法规", "规定", "航班", "票价", "库存",
            "榜单", "排名", "进展", "消息", "发布", "更新", "比赛",
            "car", "cars", "vehicle", "vehicles", "phone", "phones", "product",
            "price", "policy", "law", "flight", "inventory", "ranking",
            "release", "update", "news"
    );

    private static final List<String> AGENT_TOOL_INTENTS = List.of(
            "打开网页", "访问网页", "访问网站", "抓取网页", "网页内容",
            "下载", "保存文件", "创建文件", "修改文件", "读取文件",
            "生成pdf", "生成 pdf", "执行命令", "运行命令", "终端",
            "附近", "地图", "路线", "搜索图片", "下载图片",
            "open the website", "open this url", "download", "save file",
            "create file", "modify file", "read file", "run command",
            "execute command", "map", "route", "search images"
    );

    private final IntentClassifier intentClassifier;

    public AgentRequestRouter(IntentClassifier intentClassifier) {
        this.intentClassifier = intentClassifier;
    }

    public Route route(String message) {
        if (message == null || message.isBlank()) {
            return Route.FAST_PATH;
        }

        String normalized = normalize(message);

        // Cheap deterministic paths for commands that are already unambiguous.
        if (isExplicitCurrentTimeRequest(normalized)) {
            return Route.CURRENT_TIME;
        }

        if (URL_PATTERN.matcher(normalized).find()
                || AGENT_TOOL_INTENTS.stream().anyMatch(normalized::contains)) {
            return Route.AGENT;
        }

        if (EXPLICIT_REALTIME_INTENTS.stream().anyMatch(normalized::contains)) {
            return Route.REALTIME_SEARCH;
        }

        // The important part: unknown phrasing is interpreted semantically.
        return intentClassifier.classify(message)
                .orElseGet(() -> fallbackRoute(normalized));
    }

    public boolean requiresAgent(String message) {
        return route(message) == Route.AGENT;
    }

    private Route fallbackRoute(String normalized) {
        if (isExplicitCurrentTimeRequest(normalized)) {
            return Route.CURRENT_TIME;
        }
        if (isFreshDynamicRequest(normalized)) {
            return Route.REALTIME_SEARCH;
        }
        if (URL_PATTERN.matcher(normalized).find()
                || AGENT_TOOL_INTENTS.stream().anyMatch(normalized::contains)) {
            return Route.AGENT;
        }
        return Route.FAST_PATH;
    }

    private boolean isExplicitCurrentTimeRequest(String normalized) {
        if (EXPLICIT_TIME_INTENTS.stream().anyMatch(normalized::contains)) {
            return true;
        }

        return normalized.matches(".*(现在|当前).*(几点|时间|日期|几号|星期|周几).*")
                || normalized.matches(".*(几点|时间|日期|几号|星期|周几).*(现在|当前).*")
                || normalized.matches(".*\\b(now|current)\\b.*\\b(time|date|day)\\b.*")
                || normalized.matches(".*\\b(time|date|day)\\b.*\\b(now|current)\\b.*");
    }

    private boolean isFreshDynamicRequest(String normalized) {
        return FRESHNESS_MARKERS.stream().anyMatch(normalized::contains)
                && DYNAMIC_SUBJECTS.stream().anyMatch(normalized::contains);
    }

    private String normalize(String message) {
        return message.trim().toLowerCase(Locale.ROOT);
    }
}
