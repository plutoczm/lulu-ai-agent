package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.memory.RelationshipMemoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/memory")
@ConditionalOnProperty(prefix = "app.memory.pgvector", name = "enabled",
        havingValue = "true")
public class RelationshipMemoryController {

    private final RelationshipMemoryService memoryService;

    public RelationshipMemoryController(RelationshipMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/remember")
    public RememberResponse remember(
            @RequestBody RememberRequest request,
            HttpServletRequest httpRequest) {
        var memory = memoryService.rememberFromMessage(
                AuthSupport.scopedChatId(httpRequest, request.chatId()),
                request.message());
        return new RememberResponse(memory.isPresent(), memory.orElse(""));
    }

    @GetMapping("/search")
    public List<String> search(
            @RequestParam String chatId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            HttpServletRequest request) {
        return memoryService.search(
                        AuthSupport.scopedChatId(request, chatId), query, topK)
                .stream()
                .map(Document::getText)
                .toList();
    }

    @GetMapping("/count")
    public MemoryCount count(
            @RequestParam String chatId,
            HttpServletRequest request) {
        return new MemoryCount(memoryService.count(
                AuthSupport.scopedChatId(request, chatId)));
    }

    @DeleteMapping
    public MemoryCount clear(
            @RequestParam String chatId,
            HttpServletRequest request) {
        memoryService.clear(AuthSupport.scopedChatId(request, chatId));
        return new MemoryCount(0);
    }

    public record RememberRequest(String chatId, String message) {}
    public record RememberResponse(boolean saved, String memory) {}
    public record MemoryCount(int count) {}
}
