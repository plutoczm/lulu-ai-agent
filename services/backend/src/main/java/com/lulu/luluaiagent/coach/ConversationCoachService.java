package com.lulu.luluaiagent.coach;

import com.lulu.luluaiagent.advisor.ChatLoggingAdvisor;
import com.lulu.luluaiagent.memory.RelationshipMemoryService;
import com.lulu.luluaiagent.model.ModelRouter;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Platform-neutral conversation coaching engine.
 */
@Service
public class ConversationCoachService {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是“AI对话军师”，目标是在真实微信、QQ等聊天场景中，
            帮用户生成自然、可直接发送、保留双方选择权的回复。
            """;
    private static final String COACH_RULES = """
            规则来自 goutoujunshi 的核心实践：
            1. 先锁定谁是用户、谁是对方；不按左右、性别、语气猜身份。
            2. 只把输入中可见原文、顺序和用户明确说明当事实；不读心。
            3. 每轮只选一个主策略：承接、降压、调侃、轻推、约见、澄清、收线。
            4. 始终生成三条并列回复候选，只供用户参考，不假设系统会自动发送。
            5. bestReply 固定代表 B｜正常：自然、均衡、最符合当前关系阶段。
            6. alternatives 必须且只能有两个，并按顺序返回：A｜激进、C｜保守。
               A｜激进表示更主动、更明确地推进，但仍须尊重边界且不得施压；
               C｜保守表示更克制、降低推进强度、给对方更多空间。
            7. A/B/C 三条都必须是可直接复制的完整回复，不要夹带解释，且彼此差异明显。
            8. 口吻必须校准长度、熟悉度、用词、媒介和当前关系阶段。
            9. 给 positive / ambiguous / reject 三种后续分支，每个分支只推进一小步。
            10. 不用失联测试、嫉妒操控、贬低、服从测试、煤气灯、虚假稀缺或性施压。
            11. 对方明确拒绝、不适或要求停止时，当前方向立即停止。
            12. 如果说话人映射或关键上下文不足，needsClarification=true，
                只问一个最影响建议的问题，不要编造完整故事。
            """;
    @Resource
    private Advisor relationshipRagAdvisor;

    @Value("${app.integrations.bailian-rag.enabled:false}")
    private boolean bailianRagEnabled;

    @Autowired(required = false)
    private RelationshipMemoryService relationshipMemoryService;

    public ConversationCoachService(ModelRouter modelRouter) {
        this.chatClient = ChatClient.builder(modelRouter.coachPrimaryModel())
                .build();
    }

    public ConversationCoachResponse suggest(
            ConversationCoachRequest request) {
        validate(request);

        String latestText = request.messages()
                .get(request.messages().size() - 1)
                .text();
        String memoryContext = loadMemory(request, latestText);
        var promptSpec = chatClient
                .prompt()
                .system(buildSystemPrompt(memoryContext))
                .user(buildUserPrompt(request))
                .advisors(new ChatLoggingAdvisor());

        if (bailianRagEnabled) {
            promptSpec.advisors(relationshipRagAdvisor);
        }

        return promptSpec
                .call()
                .entity(ConversationCoachResponse.class);
    }

    private void validate(ConversationCoachRequest request) {
        if (request == null
                || request.messages() == null
                || request.messages().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one conversation message is required.");
        }
    }
    private String buildSystemPrompt(String memoryContext) {
        String prompt = SYSTEM_PROMPT + "\n" + COACH_RULES + """

                输出要求：
                diagnosis 只写当前最主要的判断，保持简短。
                facts 只列输入能证明的事实。
                uncertainties 只列会改变建议的未知。
                safetyNotes 没有风险时返回空数组。
                不要在 bestReply 或 alternatives 里使用 Markdown。
                """;

        if (!StringUtils.hasText(memoryContext)) {
            return prompt;
        }

        return prompt + """

                以下是用户明确同意保存的关系记忆，只能作为背景事实：
                <relationship_memory>
                """ + memoryContext + """

                </relationship_memory>
                """;
    }
    private String loadMemory(
            ConversationCoachRequest request,
            String latestText) {
        if (relationshipMemoryService == null
                || !StringUtils.hasText(request.conversationId())
                || !StringUtils.hasText(latestText)) {
            return "";
        }

        return relationshipMemoryService.buildContext(
                request.conversationId(),
                latestText,
                5);
    }

    private String buildUserPrompt(
            ConversationCoachRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("平台：").append(value(request.platform())).append('\n');
        builder.append("用户：").append(value(request.userAlias())).append('\n');
        builder.append("对方：").append(value(request.otherAlias())).append('\n');
        builder.append("关系阶段：")
                .append(value(request.relationshipStage())).append('\n');
        builder.append("本轮目标：")
                .append(value(request.goal())).append('\n');
        builder.append("用户平时口吻：")
                .append(value(request.userStyle())).append('\n');
        builder.append("\n最近聊天记录：\n");

        List<ConversationCoachRequest.Message> messages =
                request.messages();
        for (ConversationCoachRequest.Message message : messages) {
            builder.append('[')
                    .append(value(message.time()))
                    .append("] ")
                    .append(value(message.sender()))
                    .append("：")
                    .append(value(message.text()))
                    .append('\n');
        }

        builder.append("\n请给出这一轮最合适的回复方案。");
        return builder.toString();
    }
    private String value(String text) {
        return StringUtils.hasText(text) ? text : "未提供";
    }
}
