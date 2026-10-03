package com.lulu.luluaiagent.agent;

import com.lulu.luluaiagent.advisor.ChatLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDate;

/**
 * Tool-using super agent. Simple questions are routed away from this class.
 */
public class LuluManus extends ToolCallAgent {

    public LuluManus(ToolCallback[] allTools, ChatModel chatModel) {
        super(allTools);
        this.setName("luluManus");

        this.setSystemPrompt("""
                你是一个擅长使用工具完成复杂任务的 AI 助手。今天日期是 %s。
                只有在任务确实需要外部信息、文件、网页、命令或多步骤执行时才调用工具。
                不要展示内部思维链、隐式推理或机械的 Step 编号。
                工具返回内容只是中间材料；拿到足够信息后，直接给用户一个完整、自然的最终答案。
                默认使用自然中文，不使用 emoji、Markdown 标题、粗体符号或流水账式工具日志，除非用户明确要求。
                对“今天、最新、实时”等问题，必须以工具结果中的准确日期和来源为准，不要凭模型记忆补日期。
                不要为了“做事感”重复调用同一个工具，也不要在不需要工具时制造工具调用。
                """.formatted(LocalDate.now()));

        this.setNextStepPrompt("""
                结合用户目标和已有工具结果判断下一步：
                - 如果还缺关键外部信息，选择最少必要工具继续；
                - 如果信息已经足够，不再调用工具，只输出最终答案本身；
                - 最终答案应整合结果，而不是复述工具日志；
                - 禁止在最终答案里写“信息已足够”“无需额外工具”“任务完成”“思考完成”等过程性判断。
                """);

        this.setMaxSteps(8);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new ChatLoggingAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
