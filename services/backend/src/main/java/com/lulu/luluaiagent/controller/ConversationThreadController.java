package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import com.lulu.luluaiagent.memory.RelationshipThreadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/coach/thread")
@ConditionalOnProperty(prefix = "app.memory.pgvector", name = "enabled",
        havingValue = "true")
public class ConversationThreadController {

    private final RelationshipThreadService threadService;

    public ConversationThreadController(RelationshipThreadService threadService) {
        this.threadService = threadService;
    }

    @PostMapping("/history/import")
    public RelationshipThreadService.ThreadStatus importHistory(
            @RequestBody HistoryImportRequest request,
            HttpServletRequest httpRequest) {
        String fallback = request.conversationId();
        String personId = StringUtils.hasText(request.personId())
                ? request.personId()
                : fallback;
        String accountId = StringUtils.hasText(request.accountId())
                ? request.accountId()
                : fallback;

        return threadService.importHistory(
                AuthSupport.scopedChatId(httpRequest, fallback),
                AuthSupport.scopedChatId(httpRequest, personId),
                AuthSupport.scopedChatId(httpRequest, accountId),
                request.platform(),
                request.otherAlias(),
                request.relationshipStage(),
                request.messages());
    }

    @GetMapping("/status")
    public RelationshipThreadService.ThreadStatus status(
            @RequestParam String conversationId,
            @RequestParam(required = false) String personId,
            HttpServletRequest httpRequest) {
        String person = StringUtils.hasText(personId)
                ? personId
                : conversationId;
        return threadService.status(
                AuthSupport.scopedChatId(httpRequest, conversationId),
                AuthSupport.scopedChatId(httpRequest, person));
    }

    @DeleteMapping
    public RelationshipThreadService.ThreadStatus clearAccount(
            @RequestParam String conversationId,
            @RequestParam(required = false) String personId,
            HttpServletRequest httpRequest) {
        String person = StringUtils.hasText(personId)
                ? personId
                : conversationId;
        return threadService.clearAccount(
                AuthSupport.scopedChatId(httpRequest, conversationId),
                AuthSupport.scopedChatId(httpRequest, person));
    }

    @DeleteMapping("/person-memory")
    public RelationshipThreadService.ThreadStatus clearPersonMemory(
            @RequestParam String personId,
            @RequestParam(required = false) String conversationId,
            HttpServletRequest httpRequest) {
        String scopedThread = StringUtils.hasText(conversationId)
                ? AuthSupport.scopedChatId(httpRequest, conversationId)
                : "";
        return threadService.clearPersonMemory(
                scopedThread,
                AuthSupport.scopedChatId(httpRequest, personId));
    }

    @PostMapping("/account/reassign")
    public RelationshipThreadService.ReassignResult reassignAccount(
            @RequestBody ReassignAccountRequest request,
            HttpServletRequest httpRequest) {
        return threadService.reassignAccount(
                AuthSupport.scopedChatId(httpRequest, request.conversationId()),
                AuthSupport.scopedChatId(httpRequest, request.oldPersonId()),
                AuthSupport.scopedChatId(httpRequest, request.newPersonId()),
                AuthSupport.scopedChatId(httpRequest, request.accountId()));
    }

    public record HistoryImportRequest(
            String conversationId,
            String personId,
            String accountId,
            String platform,
            String otherAlias,
            String relationshipStage,
            List<ConversationCoachRequest.Message> messages
    ) {}

    public record ReassignAccountRequest(
            String conversationId,
            String accountId,
            String oldPersonId,
            String newPersonId
    ) {}
}
