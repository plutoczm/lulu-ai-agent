package com.lulu.luluaiagent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
        prefix = "app.memory.pgvector",
        name = "enabled",
        havingValue = "true")
public class RelationshipMemoryWriteService {

    private final RelationshipMemoryService memoryService;

    public RelationshipMemoryWriteService(
            RelationshipMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Async("relationshipMemoryExecutor")
    public void rememberCoachTurn(
            String personId,
            String sourceAccountId,
            String sourcePlatform,
            String memoryInput) {
        long started = System.nanoTime();
        try {
            memoryService.rememberFromMessage(
                    personId,
                    sourceAccountId,
                    sourcePlatform,
                    memoryInput);
            log.info(
                    "relationship memory async write completed in {} ms",
                    elapsedMillis(started));
        } catch (RuntimeException e) {
            log.warn(
                    "relationship memory async write failed after {} ms: {}",
                    elapsedMillis(started),
                    e.getMessage());
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
