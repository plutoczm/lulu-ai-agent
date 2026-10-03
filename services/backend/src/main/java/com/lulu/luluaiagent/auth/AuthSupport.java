package com.lulu.luluaiagent.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthSupport {
    public static final String USER_ATTR = "lulu.auth.user";

    private AuthSupport() {}

    public static AuthUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ATTR);
        if (!(value instanceof AuthUser user)) {
            throw new IllegalStateException("Authenticated user is required.");
        }
        return user;
    }

    public static String scopedChatId(HttpServletRequest request, String chatId) {
        AuthUser user = currentUser(request);
        String clientId = (chatId == null || chatId.isBlank()) ? "default" : chatId.trim();
        clientId = clientId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (clientId.length() > 128) clientId = clientId.substring(0, 128);
        return user.id() + "__" + clientId;
    }

    public static void requireAdmin(HttpServletRequest request) {
        if (!currentUser(request).admin()) {
            throw new SecurityException("Admin access is required.");
        }
    }
}
