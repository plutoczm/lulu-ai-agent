package com.lulu.luluaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Optional local relationship-knowledge vector store.
 */
@Configuration
@ConditionalOnProperty(
        prefix = "app.rag.local-fallback",
        name = "enabled",
        havingValue = "true"
)
public class RelationshipVectorStoreConfig {

    @Resource
    private RelationshipKnowledgeDocumentLoader relationshipKnowledgeDocumentLoader;

    @Resource
    private RelationshipTokenTextSplitter relationshipTokenTextSplitter;

    @Resource
    private RelationshipKeywordEnricher relationshipKeywordEnricher;

    @Bean
    VectorStore relationshipVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 加载文档
        List<Document> documentList = relationshipKnowledgeDocumentLoader.loadMarkdowns();
        // 自主切分文档
//        List<Document> splitDocuments = relationshipTokenTextSplitter.splitCustomized(documentList);
        // 自动补充关键词元信息
        List<Document> enrichedDocuments = relationshipKeywordEnricher.enrichDocuments(documentList);
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}
