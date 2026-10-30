package com.ofs.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 暴露给 agent 的知识库检索工具。
 * 模型自己决定何时调用；命中的原文片段原样返回，便于回答时引用，而不是改写后丢掉出处。
 */
@Component
public class KnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeTools.class);

    private final VectorStore vectorStore;

    public KnowledgeTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "检索订单政策与 FAQ。涉及退款、配送时效、支付超时、发票、订单状态含义时必须先调用本工具。"
            + "不要凭空回答政策问题。返回的是知识库原文片段，回答时请引用这些原文；没有命中就告诉用户暂时没有相关信息。")
    public String searchKnowledge(
            @ToolParam(description = "要检索的问题或关键词，例如：支付超时多久取消") String query) {

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(3).build());
        log.info("🔧 [工具] searchKnowledge(query='{}') 命中 {} 条", query, hits.size());

        if (hits.isEmpty()) {
            return "知识库没有相关资料。请如实告诉用户暂时没有相关信息，不要编造。";
        }

        StringBuilder out = new StringBuilder();
        int i = 1;
        for (Document hit : hits) {
            if (i > 1) {
                out.append('\n');
            }
            out.append('[').append(i++).append("] ").append(hit.getText());
        }
        return out.toString();
    }
}
