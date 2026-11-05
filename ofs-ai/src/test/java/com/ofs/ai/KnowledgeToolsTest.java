package com.ofs.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeToolsTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    void returnsNumberedSourceSnippets() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("订单提交后 30 分钟内未支付将自动取消。"),
                new Document("发货前可直接取消并全额退款。")
        ));

        String result = new KnowledgeTools(vectorStore).searchKnowledge("支付超时");

        assertThat(result).isEqualTo("""
                [1] 订单提交后 30 分钟内未支付将自动取消。
                [2] 发货前可直接取消并全额退款。""");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("支付超时");
        assertThat(request.getValue().getTopK()).isEqualTo(3);
    }

    @Test
    void saysWhenNothingMatches() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String result = new KnowledgeTools(vectorStore).searchKnowledge("火星配送");

        assertThat(result).contains("知识库没有相关资料");
    }
}
