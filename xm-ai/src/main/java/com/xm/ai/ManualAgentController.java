package com.xm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 演示"框架层拦截"：internalToolExecutionEnabled(false) 关闭自动执行，
 * 由我们自己写工具调用循环——模型每次"想调工具"时先交回给我们，我们看过再放行。
 * 对比：ChatController 里的 /chat 是 internalToolExecutionEnabled=true（默认），循环由框架自动跑完。
 */
@RestController
public class ManualAgentController {

    private static final Logger log = LoggerFactory.getLogger(ManualAgentController.class);

    private final ChatModel chatModel;                  // 底层模型（DeepSeek），比 ChatClient 更贴近协议
    private final ToolCallingManager toolCallingManager; // 负责真正执行工具调用的组件
    private final OrderTools orderTools;

    public ManualAgentController(ChatModel chatModel,
                                 ToolCallingManager toolCallingManager,
                                 OrderTools orderTools) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.orderTools = orderTools;
    }

    @GetMapping("/agent/manual")
    public String manual(@RequestParam String message) {

        // ★ 关键：internalToolExecutionEnabled(false) → 框架不自动执行工具，把控制权交回我们
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(orderTools))
                .internalToolExecutionEnabled(false)
                .build();

        Prompt prompt = new Prompt(List.of(new UserMessage(message)), options);
        ChatResponse response = chatModel.call(prompt);

        int round = 0;
        // 我们自己写 ReAct 循环：只要模型还想调工具，就拦一下、放行、再问
        while (response.hasToolCalls()) {
            round++;

            // —— 拦截点：模型请求调用工具，但框架没执行，交回给我们 ——
            List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
            for (AssistantMessage.ToolCall c : calls) {
                log.info("🛑 [第{}轮·拦截] 模型想调用工具: {}  参数={}", round, c.name(), c.arguments());
                // ↑ 这里就是你插入【人工审批 / 权限校验 / 参数改写 / 危险操作拦截】的地方
            }
            log.info("▶️ [第{}轮·放行] 执行上述工具调用", round);

            // 放行：真正执行工具，拿到"历史消息 + 工具结果"
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);

            // 带着工具结果组成下一轮 prompt，继续问模型
            prompt = new Prompt(toolResult.conversationHistory(), options);
            response = chatModel.call(prompt);
        }

        // 循环结束（模型不再要求调工具）→ 这就是最终答复
        return response.getResult().getOutput().getText();
    }
}
