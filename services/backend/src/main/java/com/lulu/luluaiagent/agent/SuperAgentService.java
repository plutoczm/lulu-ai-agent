package com.lulu.luluaiagent.agent;

import com.lulu.luluaiagent.model.ModelRouter;
import com.lulu.luluaiagent.model.chatgpt.CodexAppServerService;
import com.lulu.luluaiagent.tools.CurrentTimeTool;
import com.lulu.luluaiagent.tools.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SuperAgentService {

    private final ToolCallback[] allTools;
    private final ModelRouter modelRouter;
    private final AgentRequestRouter requestRouter;
    private final CodexAppServerService codexAppServerService;
    private final WebSearchTool webSearchTool;
    private final CurrentTimeTool currentTimeTool;

    public SuperAgentService(
            ToolCallback[] allTools,
            ModelRouter modelRouter,
            AgentRequestRouter requestRouter,
            CodexAppServerService codexAppServerService,
            WebSearchTool webSearchTool,
            CurrentTimeTool currentTimeTool) {
        this.allTools = allTools;
        this.modelRouter = modelRouter;
        this.requestRouter = requestRouter;
        this.codexAppServerService = codexAppServerService;
        this.webSearchTool = webSearchTool;
        this.currentTimeTool = currentTimeTool;
    }

    public SseEmitter stream(String message) {
        AgentRequestRouter.Route route = requestRouter.route(message);

        if (route == AgentRequestRouter.Route.CURRENT_TIME) {
            return streamCurrentTime();
        }
        if (route == AgentRequestRouter.Route.REALTIME_SEARCH) {
            return streamRealtimeSearch(message);
        }

        String selected = modelRouter.routes().getOrDefault("agent", "");
        if (selected.startsWith("openai-chatgpt/")) {
            String model = selected.substring("openai-chatgpt/".length());
            return codexAppServerService.stream(model, message);
        }

        if (route == AgentRequestRouter.Route.AGENT) {
            LuluManus luluManus = new LuluManus(
                    allTools, modelRouter.agentPrimaryModel());
            return luluManus.runStream(message);
        }

        return streamFastPath(message);
    }

    private SseEmitter streamCurrentTime() {
        SseEmitter emitter = new SseEmitter(30000L);
        CompletableFuture.runAsync(() -> {
            try {
                safeSend(emitter, "[STATUS] 已完成工具调用：currentDateTime");
                safeSend(emitter, currentTimeTool.currentDateTime());
                safeSend(emitter, "[DONE]");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private SseEmitter streamRealtimeSearch(String message) {
        SseEmitter emitter = new SseEmitter(180000L);

        CompletableFuture.runAsync(() -> {
            try {
                safeSend(emitter, "[STATUS] 正在联网检索实时信息：searchWeb");
                String searchResult = webSearchTool.searchWeb(message);
                safeSend(emitter, "[STATUS] 已完成工具调用：searchWeb");

                if (searchUnavailable(searchResult)) {
                    safeSend(emitter,
                            "当前联网搜索不可用，因此我不能可靠回答这个时效性问题。"
                                    + "请检查百度 Web Search 配置后再试。\n"
                                    + searchResult);
                    safeSend(emitter, "[DONE]");
                    emitter.complete();
                    return;
                }

                String clock = currentTimeTool.currentDateTime();
                String modelName = modelRouter.agentPrimaryModelName();
                ChatClient client = ChatClient.builder(modelRouter.agentPrimaryModel())
                        .build();

                String systemPrompt = """
                        你是实时信息总结助手。本次回答当前使用的模型是：%s。
                        用户的问题属于时效性问题，必须严格基于下面提供的实时联网检索结果回答。
                        当前系统时钟：%s

                        规则：
                        1. 禁止用模型训练记忆补充检索结果中没有出现的“最新”事实。
                        2. 如果不同来源冲突，要明确指出冲突，不要擅自选一个当事实。
                        3. 如果检索结果不足以确认，就直接说“当前检索结果不足以确认”。
                        4. 涉及“现在、最新、目前、今天”时，在答案中说明信息截至当前系统日期。
                        5. 保留关键来源名称和 URL，方便用户核验。
                        6. 默认使用简洁自然的中文，不展示内部推理过程。
                        """.formatted(modelName, clock);

                String userPrompt = """
                        用户问题：
                        %s

                        实时联网检索结果：
                        %s
                        """.formatted(message, searchResult);

                String answer = client.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                safeSend(emitter, answer == null || answer.isBlank()
                        ? "已完成实时检索，但模型没有生成可用摘要。"
                        : answer.trim());
                safeSend(emitter, "[DONE]");
                emitter.complete();
            } catch (Exception e) {
                log.error("Realtime search workflow failed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private boolean searchUnavailable(String result) {
        if (result == null || result.isBlank()) {
            return true;
        }
        return result.startsWith("Baidu search is not configured")
                || result.startsWith("Baidu search failed")
                || result.startsWith("No Baidu search results found");
    }

    private SseEmitter streamFastPath(String message) {
        SseEmitter emitter = new SseEmitter(180000L);
        String modelName = modelRouter.agentPrimaryModelName();
        ChatClient client = ChatClient.builder(modelRouter.agentPrimaryModel())
                .build();

        String systemPrompt = """
                你是这个应用的通用 AI 助手。普通、非时效性问题直接自然回答，不启动工具工作流。
                不展示内部思维过程，不使用机械的 Step 编号。
                默认用简洁、自然的中文，不使用 emoji、Markdown 标题、粗体符号或报告式模板；只有用户明确要求时才展开。
                如果用户询问“你是什么模型”，请准确说明本次回答当前使用的模型是：%s。
                如果问题明显依赖今天、现在、最新、实时信息，不要猜测；这类问题应该由上游实时 workflow 处理。
                应用还会使用 qwen3:8b 处理本地长期记忆提取，并可调用百炼知识库和百度搜索等能力。
                """.formatted(modelName);

        client.prompt()
                .system(systemPrompt)
                .user(message)
                .stream()
                .content()
                .subscribe(
                        chunk -> safeSend(emitter, chunk),
                        emitter::completeWithError,
                        () -> {
                            safeSend(emitter, "[DONE]");
                            emitter.complete();
                        }
                );
        return emitter;
    }

    private void safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(data);
        } catch (Exception e) {
            log.debug("SSE client disconnected: {}", e.getMessage());
        }
    }
}
