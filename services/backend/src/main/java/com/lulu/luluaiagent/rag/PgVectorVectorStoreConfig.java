package com.lulu.luluaiagent.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * Dedicated PGVector store for user-scoped relationship memory.
 *
 * General love knowledge lives in Alibaba Cloud Bailian. This table stores
 * only user-approved, compact long-term relationship facts.
 */
@Configuration
@ConditionalOnProperty(
        prefix = "app.memory.pgvector",
        name = "enabled",
        havingValue = "true"
)
public class PgVectorVectorStoreConfig {
    @Bean("relationshipMemoryVectorStore")
    public VectorStore relationshipMemoryVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("relationship_memory")
                .maxDocumentBatchSize(1000)
                .build();
    }
}
