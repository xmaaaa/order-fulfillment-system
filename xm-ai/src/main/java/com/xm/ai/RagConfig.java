package com.xm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RAG 的知识库装配。
 * 用内存向量库 SimpleVectorStore（学习够用；生产可换 Redis/pgvector，接口不变）。
 * 启动时把订单相关 FAQ 文本向量化后存进去。
 */
@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // EmbeddingModel 由 transformers starter 自动装配（本地 ONNX 模型）
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // 知识库：真实项目里通常来自文档/数据库/网页，这里先内置几条订单 FAQ
        List<Document> docs = List.of(
                new Document("订单状态说明：DRAFT 表示草稿未提交；SUBMITTED 已提交待支付；PAID 已支付；SHIPPED 已发货；CANCELLED 已取消。"),
                new Document("退款政策：订单在发货(SHIPPED)之前可直接取消并全额退款；发货后需申请退货，商品签收后 7 天内支持无理由退货。"),
                new Document("配送时效：普通订单下单后 48 小时内发货，通常 3-5 个工作日送达；偏远地区可能顺延。"),
                new Document("支付与超时：支持微信、支付宝、银行卡支付。订单提交(SUBMITTED)后 30 分钟内未支付将自动取消。"),
                new Document("发票说明：支持电子普通发票和增值税专用发票，可在订单完成后 30 天内申请开具。")
        );

        // add() 内部会调用 embeddingModel 把每条文本转成向量再存储
        store.add(docs);
        log.info("✅ 知识库已加载 {} 条文档到向量库", docs.size());
        return store;
    }
}
