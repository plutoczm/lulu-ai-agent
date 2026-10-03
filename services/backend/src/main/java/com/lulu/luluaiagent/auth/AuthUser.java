package com.lulu.luluaiagent.auth;

import java.util.UUID;

public record AuthUser(
        UUID id,
        String email,
        String displayName,
        boolean admin
) {}
