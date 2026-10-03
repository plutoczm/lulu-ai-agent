package com.lulu.luluaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloud RAG advisor using the current Alibaba Cloud Model Studio RAG REST API.
 */
@Configuration
public class RelationshipRagCloudConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${app.integrations.bailian-rag.workspace-id:}")
    private String workspaceId;

    @Value("${app.integrations.bailian-rag.index-id:}")
    private String indexId;

    @Bean
    public Advisor relationshipRagAdvisor() {
        DocumentRetriever documentRetriever =
                new BailianKnowledgeDocumentRetriever(
                        dashScopeApiKey, workspaceId, indexId, 5);
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(RelationshipContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
