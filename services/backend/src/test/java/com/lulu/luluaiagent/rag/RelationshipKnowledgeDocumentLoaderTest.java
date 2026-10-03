package com.lulu.luluaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RelationshipKnowledgeDocumentLoaderTest {

    @Resource
    private RelationshipKnowledgeDocumentLoader relationshipKnowledgeDocumentLoader;

    @Test
    void loadMarkdowns() {
        relationshipKnowledgeDocumentLoader.loadMarkdowns();
    }
}