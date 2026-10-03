package com.lulu.luluaiagent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class UsageLimitInterceptor implements HandlerInterceptor {
    private final UsageLimitService usageLimitService;
    private final ObjectMapper objectMapper;

    public UsageLimitInterceptor(
            UsageLimitService usageLimitService,
            ObjectMapper objectMapper) {
        this.usageLimitService = usageLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        AuthUser user = AuthSupport.currentUser(request);
        UsageLimitService.Usage usage = usageLimitService.consume(user.id());
        response.setHeader("X-RateLimit-Limit", String.valueOf(usage.limit()));
        response.setHeader("X-RateLimit-Used", String.valueOf(usage.used()));
        if (usage.allowed()) return true;

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "DAILY_LIMIT_REACHED",
                "message", "Daily AI request limit reached.",
                "limit", usage.limit()));
        return false;
    }
}
