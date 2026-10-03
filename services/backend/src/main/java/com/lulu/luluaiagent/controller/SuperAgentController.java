package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.agent.SuperAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai/manus")
public class SuperAgentController {

    private final SuperAgentService superAgentService;

    public SuperAgentController(SuperAgentService superAgentService) {
        this.superAgentService = superAgentService;
    }

    @GetMapping("/chat")
    public SseEmitter chat(String message) {
        return superAgentService.stream(message);
    }
}
