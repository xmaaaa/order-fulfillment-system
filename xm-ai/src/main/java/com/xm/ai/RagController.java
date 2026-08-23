package com.xm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 手写 RAG：把"检索 → 增强 → 生成"三步显式写出来，方便理解原理。
 */
@RestController
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/rag")
    public String rag(@RequestParam String question) {

        // ① 检索(Retrieval)：把问题也向量化，在向量库里找语义最接近的前 3 条知识
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(3).build());
        log.info("🔍 [RAG] 问题='{}' 检索到 {} 条相关知识", question, hits.size());

        String context = hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // ② 增强(Augmentation)：把检索到的知识拼进提示词，约束模型"只依据资料回答"
        String prompt = """
                你是订单客服助手。请【只根据】下面的已知资料回答用户问题；
                如果资料里没有相关信息，就如实说"暂时没有相关信息"，不要编造。

                【已知资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        // ③ 生成(Generation)：交给 DeepSeek 基于资料生成最终答复
        return chatClient.prompt().user(prompt).call().content();
    }
}
