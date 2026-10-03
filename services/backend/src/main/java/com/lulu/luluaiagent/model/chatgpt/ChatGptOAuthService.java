package com.lulu.luluaiagent.model.chatgpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatGptOAuthService {

    private static final String ISSUER = "https://auth.openai.com";
    private static final String AUTHORIZE_URL =
            ISSUER + "/api/accounts/authorize";
    private static final String TOKEN_URL =
            ISSUER + "/api/accounts/oauth/token";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String REQUIRED_SCOPE =
            "chatgpt.tokens.use.direct";
    private static final String SCOPES =
            "openid profile email offline_access resource.invoke "
                    + REQUIRED_SCOPE;
    private static final String AGENT_NAME = "LULU AI";

    private final ChatGptCredentialStore store;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile JwtDecoder jwtDecoder;
    private final SecureRandom secureRandom = new SecureRandom();

    private final AtomicReference<PendingLogin> pending =
            new AtomicReference<>();
    public ChatGptOAuthService(
            ChatGptCredentialStore store,
            ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ChatGptAccountStatus status() {
        Optional<ChatGptCredential> credential = store.read();
        if (credential.isEmpty()) {
            return ChatGptAccountStatus.signedOut();
        }
        ChatGptCredential c = credential.get();
        return new ChatGptAccountStatus(
                true,
                c.hasPlanUsageScope(),
                c.email(),
                c.subject(),
                c.clientId(),
                c.expiresAt());
    }

    public synchronized ChatGptLoginStart startLogin(boolean forceNew) {
        stopPendingServer();

        ChatGptCredential existing =
                (!forceNew) ? store.read().orElse(null) : null;
        String requestedClientId = existing != null
                ? existing.clientId()
                : "dynamic_agent_client";
        String state = randomUrlToken(24);
        String nonce = randomUrlToken(24);
        String verifier = randomUrlToken(32);
        String challenge = sha256Base64Url(verifier);
        String attemptId = UUID.randomUUID().toString();

        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor =
                    Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(
                                r, "chatgpt-oauth-callback");
                        t.setDaemon(true);
                        return t;
                    });
            server.setExecutor(executor);

            int port = server.getAddress().getPort();
            String redirectUri =
                    "http://127.0.0.1:" + port + "/auth/callback";

            PendingLogin attempt = new PendingLogin(
                    attemptId, state, nonce, verifier,
                    requestedClientId, redirectUri,
                    existing, server, executor);

            server.createContext(
                    "/auth/callback",
                    exchange -> handleCallback(exchange, attempt));
            pending.set(attempt);
            server.start();

            return new ChatGptLoginStart(
                    buildAuthorizationUrl(attempt, challenge, forceNew),
                    attemptId,
                    redirectUri);
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start ChatGPT OAuth callback server.", e);
        }
    }

    private String buildAuthorizationUrl(
            PendingLogin attempt,
            String challenge,
            boolean forceNew) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", attempt.requestedClientId());
        params.put("ext_agent_host_id", store.hostId());
        params.put("response_type", "code");
        params.put("redirect_uri", attempt.redirectUri());
        params.put("scope", SCOPES);
        params.put("resource", RESOURCE);
        params.put("state", attempt.state());
        params.put("nonce", attempt.nonce());
        params.put("code_challenge_method", "S256");
        params.put("code_challenge", challenge);

        if (attempt.existing() == null || forceNew) {
            params.put("agent_name_hint", AGENT_NAME);
        }
        else {
            if (notBlank(attempt.existing().idToken())) {
                params.put("id_token_hint", attempt.existing().idToken());
            }
            if (notBlank(attempt.existing().email())) {
                params.put("login_hint", attempt.existing().email());
            }
        }

        return AUTHORIZE_URL + "?" + form(params);
    }
    private void handleCallback(
            HttpExchange exchange,
            PendingLogin attempt) throws IOException {
        try {
            Map<String, String> query =
                    parseQuery(exchange.getRequestURI().getRawQuery());

            if (!constantEquals(attempt.state(), query.get("state"))) {
                sendHtml(exchange, 400,
                        "登录失败",
                        "OAuth state 校验失败，请重新发起登录。");
                return;
            }

            String oauthError = query.get("error");
            if (notBlank(oauthError)) {
                sendHtml(exchange, 400,
                        "登录未完成",
                        "OpenAI 未完成本次授权。");
                return;
            }

            String code = query.get("code");
            if (!notBlank(code)) {
                sendHtml(exchange, 400,
                        "登录失败",
                        "授权回调缺少 authorization code。");
                return;
            }

            String clientId = resolveIssuedClientId(attempt, query);
            TokenResponse token =
                    exchangeAuthorizationCode(attempt, clientId, code);
            Jwt identity = validateIdentityToken(
                    token.idToken(), clientId, attempt.nonce());
            String subject = identity.getSubject();
            String email = identity.getClaimAsString("email");

            if (attempt.existing() != null
                    && notBlank(attempt.existing().subject())
                    && !attempt.existing().subject().equals(subject)) {
                sendHtml(exchange, 400,
                        "登录失败",
                        "返回的 ChatGPT 账号与当前已保存账号不一致。");
                return;
            }

            List<String> scopes = splitScopes(token.scope());
            String refreshToken = notBlank(token.refreshToken())
                    ? token.refreshToken()
                    : (attempt.existing() != null
                        ? attempt.existing().refreshToken()
                        : null);

            ChatGptCredential credential = new ChatGptCredential(
                    email,
                    identity.getIssuer() != null
                            ? identity.getIssuer().toString()
                            : ISSUER,
                    subject,
                    clientId,
                    store.hostId(),
                    token.idToken(),
                    token.accessToken(),
                    refreshToken,
                    token.tokenType(),
                    token.expiresIn(),
                    scopes,
                    Instant.now());

            store.write(credential);

            String planMessage = credential.hasPlanUsageScope()
                    ? "ChatGPT Plan 权限已授权，可以使用账号可用模型。"
                    : "账号登录成功，但未授予 ChatGPT Plan 推理权限。";
            sendHtml(exchange, 200, "噜噜已连接 ChatGPT", planMessage);
        }
        catch (Exception e) {
            sendHtml(exchange, 500,
                    "登录失败",
                    "授权交换或身份校验失败，请回到噜噜重新登录。");
        }
        finally {
            stopAttempt(attempt);
        }
    }
    private String resolveIssuedClientId(
            PendingLogin attempt,
            Map<String, String> query) {
        String callbackClientId = query.get("client_id");

        if ("dynamic_agent_client".equals(attempt.requestedClientId())) {
            if (!notBlank(callbackClientId)) {
                throw new IllegalStateException(
                        "Dynamic registration did not return client_id.");
            }
            return callbackClientId;
        }

        if (notBlank(callbackClientId)
                && !attempt.requestedClientId().equals(callbackClientId)) {
            throw new IllegalStateException(
                    "OAuth callback client_id mismatch.");
        }
        return attempt.requestedClientId();
    }

    private TokenResponse exchangeAuthorizationCode(
            PendingLogin attempt,
            String clientId,
            String code) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "authorization_code");
        fields.put("client_id", clientId);
        fields.put("code", code);
        fields.put("code_verifier", attempt.verifier());
        fields.put("redirect_uri", attempt.redirectUri());
        fields.put("resource", RESOURCE);

        return tokenRequest(fields);
    }
    private TokenResponse tokenRequest(
            Map<String, String> fields) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "OpenAI token endpoint returned HTTP "
                            + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        return new TokenResponse(
                json.path("access_token").asText(null),
                json.path("refresh_token").asText(null),
                json.path("id_token").asText(null),
                json.path("token_type").asText("Bearer"),
                json.path("expires_in").asLong(3600),
                json.path("scope").asText(""));
    }
    private JwtDecoder jwtDecoder() {
        JwtDecoder current = jwtDecoder;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (jwtDecoder == null) {
                jwtDecoder = JwtDecoders.fromIssuerLocation(ISSUER);
            }
            return jwtDecoder;
        }
    }

    private Jwt validateIdentityToken(
            String idToken,
            String clientId,
            String expectedNonce) {
        if (!notBlank(idToken)) {
            throw new IllegalStateException("ID token is missing.");
        }

        Jwt jwt = jwtDecoder().decode(idToken);

        if (jwt.getIssuer() == null
                || !ISSUER.equals(jwt.getIssuer().toString())) {
            throw new IllegalStateException("ID token issuer mismatch.");
        }
        if (!jwt.getAudience().contains(clientId)) {
            throw new IllegalStateException("ID token audience mismatch.");
        }

        String nonce = jwt.getClaimAsString("nonce");
        if (!constantEquals(expectedNonce, nonce)) {
            throw new IllegalStateException("ID token nonce mismatch.");
        }
        if (!notBlank(jwt.getSubject())) {
            throw new IllegalStateException("ID token subject is missing.");
        }
        return jwt;
    }

    public synchronized ChatGptCredential accessCredential() {
        ChatGptCredential credential = store.read()
                .orElseThrow(() -> new IllegalStateException(
                        "ChatGPT account is not signed in."));

        if (!credential.hasPlanUsageScope()) {
            throw new IllegalStateException(
                    "ChatGPT plan usage scope is not granted.");
        }
        if (credential.expiresAt()
                .isAfter(Instant.now().plusSeconds(120))) {
            return credential;
        }

        if (!notBlank(credential.refreshToken())) {
            throw new IllegalStateException(
                    "ChatGPT refresh token is unavailable.");
        }

        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("grant_type", "refresh_token");
            fields.put("client_id", credential.clientId());
            fields.put("refresh_token", credential.refreshToken());
            fields.put("resource", RESOURCE);

            TokenResponse refreshed = tokenRequest(fields);
            String idToken = notBlank(refreshed.idToken())
                    ? refreshed.idToken()
                    : credential.idToken();
            String refreshToken = notBlank(refreshed.refreshToken())
                    ? refreshed.refreshToken()
                    : credential.refreshToken();
            List<String> scopes = splitScopes(refreshed.scope());
            if (scopes.isEmpty()) {
                scopes = credential.scopes();
            }
            ChatGptCredential updated = new ChatGptCredential(
                    credential.email(),
                    credential.issuer(),
                    credential.subject(),
                    credential.clientId(),
                    credential.extAgentHostId(),
                    idToken,
                    refreshed.accessToken(),
                    refreshToken,
                    refreshed.tokenType(),
                    refreshed.expiresIn(),
                    scopes,
                    Instant.now());
            store.write(updated);
            return updated;
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to refresh ChatGPT access token.", e);
        }
    }

    public synchronized boolean logout() {
        ChatGptCredential credential = store.read().orElse(null);
        if (credential == null) {
            return true;
        }

        boolean revoked = revokeRefreshToken(credential);
        store.clear();
        return revoked;
    }
    private boolean revokeRefreshToken(
            ChatGptCredential credential) {
        if (!notBlank(credential.refreshToken())) {
            return true;
        }
        try {
            HttpRequest discovery = HttpRequest.newBuilder()
                    .uri(URI.create(
                            ISSUER + "/.well-known/openid-configuration"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> discoveryResponse = httpClient.send(
                    discovery, HttpResponse.BodyHandlers.ofString());
            if (discoveryResponse.statusCode() / 100 != 2) {
                return false;
            }

            String endpoint = objectMapper
                    .readTree(discoveryResponse.body())
                    .path("revocation_endpoint")
                    .asText(null);
            if (!notBlank(endpoint)) {
                return false;
            }

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("token", credential.refreshToken());
            fields.put("token_type_hint", "refresh_token");
            fields.put("client_id", credential.clientId());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header(
                            "Content-Type",
                            "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        }
        catch (Exception e) {
            return false;
        }
    }

    private void stopPendingServer() {
        PendingLogin current = pending.getAndSet(null);
        if (current != null) {
            current.server().stop(0);
            current.executor().shutdown();
        }
    }

    private void stopAttempt(PendingLogin attempt) {
        pending.compareAndSet(attempt, null);
        attempt.server().stop(0);
        attempt.executor().shutdown();
    }
    private void sendHtml(
            HttpExchange exchange,
            int status,
            String title,
            String message) throws IOException {
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <title>%s</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0;
                      min-height: 100vh; display: grid; place-items: center;
                      background: #f7f8fb; color: #172344; }
                    .card { width: min(520px, calc(100%% - 40px));
                      padding: 32px; background: white; border-radius: 18px;
                      box-shadow: 0 18px 60px rgba(31,45,90,.12); }
                    h1 { margin: 0 0 12px; font-size: 24px; }
                    p { margin: 0; color: #667085; line-height: 1.7; }
                  </style>
                </head>
                <body><div class="card"><h1>%s</h1><p>%s</p></div></body>
                </html>
                """.formatted(
                        escapeHtml(title),
                        escapeHtml(title),
                        escapeHtml(message));

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    private String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(
                            value.getBytes(StandardCharsets.US_ASCII)));
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to generate PKCE challenge.", e);
        }
    }

    private String form(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                parts.add(encode(entry.getKey())
                        + "=" + encode(entry.getValue()));
            }
        }
        return String.join("&", parts);
    }
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> splitScopes(String scope) {
        if (!notBlank(scope)) {
            return List.of();
        }
        return java.util.Arrays.stream(scope.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record TokenResponse(
            String accessToken,
            String refreshToken,
            String idToken,
            String tokenType,
            long expiresIn,
            String scope) {
    }
    private record PendingLogin(
            String attemptId,
            String state,
            String nonce,
            String verifier,
            String requestedClientId,
            String redirectUri,
            ChatGptCredential existing,
            HttpServer server,
            ExecutorService executor) {
    }
}
