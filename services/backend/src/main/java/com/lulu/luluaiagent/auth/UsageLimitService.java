package com.lulu.luluaiagent.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UsageLimitService {
    private final JdbcTemplate jdbcTemplate;
    private final int dailyLimit;

    public UsageLimitService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.auth.daily-ai-limit:200}") int dailyLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyLimit = Math.max(1, dailyLimit);
    }

    @PostConstruct
    public void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_usage_daily (
                    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                    usage_date DATE NOT NULL DEFAULT CURRENT_DATE,
                    request_count INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, usage_date)
                )
                """);
    }

    public Usage consume(UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                INSERT INTO app_usage_daily(user_id, usage_date, request_count)
                VALUES (?, CURRENT_DATE, 1)
                ON CONFLICT (user_id, usage_date)
                DO UPDATE SET request_count = app_usage_daily.request_count + 1
                RETURNING request_count
                """, Integer.class, userId);
        int used = count == null ? dailyLimit : count;
        return new Usage(used, dailyLimit, used <= dailyLimit);
    }

    public record Usage(int used, int limit, boolean allowed) {}
}
