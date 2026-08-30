package com.ofs.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * M1：最小可用的对话接口。
 *
 * ChatClient 是 Spring AI 的"高层门面"——你不用关心底层是 DeepSeek 还是别的模型，
 * 统一用 prompt() → user(消息) → call() → content() 拿到回答。
 * 换模型只改 pom 依赖和配置，这段代码不用动。
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final OrderTools orderTools;

    // Agent 系统提示词：定义角色 + ReAct 式多步工作方式。这是"聊天机器人→agent"的关键。
    private static final String AGENT_SYSTEM_PROMPT = """
            你是订单运营智能助手，可以调用工具查询和操作订单系统。

            工作方式（重要）：
            1. 先理解用户意图。若任务需要多个步骤，请规划出合理顺序，并【逐步调用工具】完成。
               例如"下单并提交支付"应依次：创建草稿订单 → 提交 → 支付。
            2. 每一步都要【基于上一步工具返回的真实结果】再决定下一步，绝不臆造订单号或结果。
            3. 若某步工具返回失败，分析原因（如订单状态不满足），据实告知用户，不要强行继续。
            4. 涉及支付、发货、取消等写操作时保持谨慎、按业务状态顺序执行。
            5. 全部完成后，用简洁中文总结你【执行了哪几步、每步结果如何】。
            """;

    // 构建 ChatClient 时挂上"记忆顾问"：它会自动把该会话的历史消息拼进每次请求，并把新问答存回记忆。
    public ChatController(ChatClient.Builder builder, OrderTools orderTools, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem(AGENT_SYSTEM_PROMPT)   // ★ 所有请求都带上 agent 人格与工作方式
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.orderTools = orderTools;
    }

    /**
     * 访问示例：GET /chat?message=...&conversationId=u1
     * conversationId 用来区分不同会话；同一个 id 的多次请求共享上下文（记忆）。
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message,
                       @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()   // 开始构建一次对话请求
                .user(message)        // 用户说的话
                .tools(orderTools)     // ★ 把订单工具交给模型；它会自行决定是否调用其中的 @Tool 方法
                // ★ 指定会话 ID → 记忆顾问按此 id 存取历史，实现多轮上下文
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()               // 同步调用 DeepSeek（含"模型请求调用→执行→回填→再生成"的完整来回）
                .content();           // 取纯文本回答
    }

    /**
     * 流式版：返回 SSE 事件流，回答一个字一个字地吐出来（打字机效果）。
     * 关键区别：.stream() 代替 .call()，返回 Flux<String>；产出类型为 text/event-stream。
     * 访问示例：GET /chat/stream?message=...（用支持 SSE 的客户端或 curl -N 观察）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(defaultValue = "你好") String message,
                                   @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .tools(orderTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()             // ★ 流式：返回 Flux<String>，边生成边推送
                .content();
    }
}
