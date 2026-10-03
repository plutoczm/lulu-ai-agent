package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.model.chatgpt.ChatGptAccountStatus;
import com.lulu.luluaiagent.model.chatgpt.ChatGptLoginStart;
import com.lulu.luluaiagent.model.chatgpt.ChatGptModelInfo;
import com.lulu.luluaiagent.model.chatgpt.ChatGptOAuthService;
import com.lulu.luluaiagent.model.chatgpt.ChatGptPlanProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/auth/chatgpt")
public class ChatGptAuthController {

    private final ChatGptOAuthService oauth;
    private final ChatGptPlanProvider provider;

    public ChatGptAuthController(
            ChatGptOAuthService oauth,
            ChatGptPlanProvider provider) {
        this.oauth = oauth;
        this.provider = provider;
    }

    @GetMapping("/status")
    public ChatGptAccountStatus status(HttpServletRequest request) {
        AuthSupport.requireAdmin(request);
        return oauth.status();
    }

    @PostMapping("/start")
    public ChatGptLoginStart start(
            @RequestParam(defaultValue = "false") boolean forceNew,
            HttpServletRequest request) {
        AuthSupport.requireAdmin(request);
        return oauth.startLogin(forceNew);
    }

    @GetMapping("/models")
    public List<ChatGptModelInfo> models(HttpServletRequest request) {
        AuthSupport.requireAdmin(request);
        return provider.refresh();
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        AuthSupport.requireAdmin(request);
        boolean revoked = oauth.logout();
        provider.refresh();
        return Map.of(
                "signedOut", true,
                "remoteRevoked", revoked);
    }
}
