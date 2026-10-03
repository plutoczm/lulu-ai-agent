package com.lulu.luluaiagent.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public static final String COOKIE_NAME = "LULU_SESSION";

    private final AuthService authService;
    private final boolean secureCookie;
    private final long sessionDays;

    public AuthController(
            AuthService authService,
            @Value("${app.auth.secure-cookie:false}") boolean secureCookie,
            @Value("${app.auth.session-days:30}") long sessionDays) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.sessionDays = sessionDays;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthSession session = authService.register(
                request.email(), request.password(), request.displayName());
        setSessionCookie(httpRequest, response, session.token());
        return Map.of("user", session.user());
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthSession session = authService.login(request.email(), request.password());
        setSessionCookie(httpRequest, response, session.token());
        return Map.of("user", session.user());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(readCookie(request));
        clearSessionCookie(request, response);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return Map.of("user", AuthSupport.currentUser(request));
    }

    private void setSessionCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(Math.max(1, sessionDays)))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(
            HttpServletRequest request,
            HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return secureCookie
                || request.isSecure()
                || "https".equalsIgnoreCase(forwardedProto);
    }

    static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public record RegisterRequest(String email, String password, String displayName) {}
    public record LoginRequest(String email, String password) {}
}
