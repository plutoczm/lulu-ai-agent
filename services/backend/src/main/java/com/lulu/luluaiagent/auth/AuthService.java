package com.lulu.luluaiagent.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final Duration sessionTtl;
    private final List<String> adminEmails;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.auth.session-days:30}") long sessionDays,
            @Value("${app.auth.admin-emails:}") String adminEmails) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionTtl = Duration.ofDays(Math.max(1, sessionDays));
        this.adminEmails = java.util.Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }

    @PostConstruct
    public void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    id UUID PRIMARY KEY,
                    email VARCHAR(320) NOT NULL UNIQUE,
                    display_name VARCHAR(80) NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_session (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                    token_hash CHAR(64) NOT NULL UNIQUE,
                    expires_at TIMESTAMPTZ NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_session_user ON app_session(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_session_expiry ON app_session(expires_at)");
    }

    public AuthSession register(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        String name = normalizeDisplayName(displayName, normalizedEmail);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, normalizedEmail);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        UUID userId = UUID.randomUUID();
        boolean admin = adminEmails.contains(normalizedEmail);
        jdbcTemplate.update("""
                INSERT INTO app_user(id, email, display_name, password_hash, is_admin)
                VALUES (?, ?, ?, ?, ?)
                """, userId, normalizedEmail, name, passwordEncoder.encode(password), admin);
        return createSession(loadUser(userId).orElseThrow());
    }

    public AuthSession login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT id, email, display_name, password_hash, is_admin
                FROM app_user WHERE email = ?
                """, (rs, rowNum) -> mapUserRow(rs), normalizedEmail);
        if (rows.isEmpty() || !passwordEncoder.matches(password == null ? "" : password,
                rows.get(0).passwordHash())) {
            throw new IllegalArgumentException("Email or password is incorrect.");
        }
        cleanupExpiredSessions();
        return createSession(rows.get(0).user());
    }

    public Optional<AuthUser> resolveToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String tokenHash = sha256(rawToken);
        List<AuthUser> users = jdbcTemplate.query("""
                SELECT u.id, u.email, u.display_name, u.is_admin
                FROM app_session s
                JOIN app_user u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > NOW()
                """, (rs, rowNum) -> new AuthUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getBoolean("is_admin")), tokenHash);
        if (users.isEmpty()) return Optional.empty();
        jdbcTemplate.update("UPDATE app_session SET last_seen_at = NOW() WHERE token_hash = ?", tokenHash);
        return Optional.of(users.get(0));
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            jdbcTemplate.update("DELETE FROM app_session WHERE token_hash = ?", sha256(rawToken));
        }
    }

    private AuthSession createSession(AuthUser user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(sessionTtl);
        jdbcTemplate.update("""
                INSERT INTO app_session(id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), user.id(), sha256(token), expiresAt);
        return new AuthSession(user, token);
    }

    private Optional<AuthUser> loadUser(UUID id) {
        List<AuthUser> rows = jdbcTemplate.query("""
                SELECT id, email, display_name, is_admin FROM app_user WHERE id = ?
                """, (rs, rowNum) -> new AuthUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getBoolean("is_admin")), id);
        return rows.stream().findFirst();
    }

    private UserRow mapUserRow(ResultSet rs) throws SQLException {
        AuthUser user = new AuthUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getBoolean("is_admin"));
        return new UserRow(user, rs.getString("password_hash"));
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("A valid email is required.");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be 10-128 characters.");
        }
    }

    private String normalizeDisplayName(String displayName, String email) {
        String value = displayName == null ? "" : displayName.trim();
        if (value.isBlank()) value = email.substring(0, email.indexOf('@'));
        if (value.length() > 80) value = value.substring(0, 80);
        return value;
    }

    private void cleanupExpiredSessions() {
        jdbcTemplate.update("DELETE FROM app_session WHERE expires_at <= NOW()");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash session token.", e);
        }
    }

    private record UserRow(AuthUser user, String passwordHash) {}
}
