package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.coach.ConversationCoachRequest;
import com.lulu.luluaiagent.coach.ConversationCoachResponse;
import com.lulu.luluaiagent.coach.ConversationCoachService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/coach")
public class ConversationCoachController {

    private final ConversationCoachService coachService;

    public ConversationCoachController(ConversationCoachService coachService) {
        this.coachService = coachService;
    }

    @PostMapping("/suggest")
    public ConversationCoachResponse suggest(
            @RequestBody ConversationCoachRequest request,
            HttpServletRequest httpRequest) {
        return coachService.suggest(scope(request, httpRequest));
    }

    @PostMapping("/suggest/quick")
    public ConversationCoachResponse suggestQuick(
            @RequestBody ConversationCoachRequest request,
            HttpServletRequest httpRequest) {
        return coachService.suggestQuick(scope(request, httpRequest));
    }

    private ConversationCoachRequest scope(
            ConversationCoachRequest request,
            HttpServletRequest httpRequest) {
        String fallback = request.conversationId();
        String personId = StringUtils.hasText(request.personId())
                ? request.personId()
                : fallback;
        String accountId = StringUtils.hasText(request.accountId())
                ? request.accountId()
                : fallback;

        return new ConversationCoachRequest(
                request.platform(),
                AuthSupport.scopedChatId(httpRequest, fallback),
                AuthSupport.scopedChatId(httpRequest, personId),
                AuthSupport.scopedChatId(httpRequest, accountId),
                request.userAlias(),
                request.otherAlias(),
                request.relationshipStage(),
                request.goal(),
                request.userStyle(),
                request.messages());
    }
}
