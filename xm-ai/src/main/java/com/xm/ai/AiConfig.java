package com.xm.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置。
 * MessageWindowChatMemory：保留每个会话最近 N 条消息（滑动窗口），默认存在内存里。
 * 生产可换成基于 Redis/JDBC 的 ChatMemoryRepository，接口不变。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)   // 每个会话最多记住最近 20 条消息
                .build();
    }
}
