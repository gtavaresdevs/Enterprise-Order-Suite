package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.security.ratelimit.RateLimitDecision;
import com.enterprise.ordersuite.security.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter forgotPasswordLimiter;
    private final RateLimiter loginLimiter;
    private final RateLimiter resetPasswordLimiter;
    private final RateLimiter refreshLimiter;
    private final RateLimiter logoutLimiter;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthRateLimitFilter(
            RateLimiter forgotPasswordLimiter,
            RateLimiter loginLimiter,
            RateLimiter resetPasswordLimiter,
            RateLimiter refreshLimiter,
            RateLimiter logoutLimiter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.forgotPasswordLimiter = forgotPasswordLimiter;
        this.loginLimiter = loginLimiter;
        this.resetPasswordLimiter = resetPasswordLimiter;
        this.refreshLimiter = refreshLimiter;
        this.logoutLimiter = logoutLimiter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        return !(
                path.equals("/auth/forgot-password")
                        || path.equals("/auth/login")
                        || path.equals("/auth/reset-password")
                        || path.equals("/auth/refresh")
                        || path.equals("/auth/logout")
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = extractClientIp(request);

        RateLimitDecision decision = getRateLimitDecision(path, ip);

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "code", "RATE_LIMITED",
                "message", "Too many requests. Please try again later.",
                "timestamp", Instant.now(clock).toString()
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private RateLimitDecision getRateLimitDecision(String path, String ip) {
        RateLimiter limiter;
        String keyPrefix;

        if (path.equals("/auth/forgot-password")) {
            limiter = forgotPasswordLimiter;
            keyPrefix = "FORGOT_PASSWORD";
        } else if (path.equals("/auth/login")) {
            limiter = loginLimiter;
            keyPrefix = "LOGIN";
        } else if (path.equals("/auth/reset-password")) {
            limiter = resetPasswordLimiter;
            keyPrefix = "RESET_PASSWORD";
        } else if (path.equals("/auth/refresh")) {
            limiter = refreshLimiter;
            keyPrefix = "REFRESH";
        } else {
            limiter = logoutLimiter;
            keyPrefix = "LOGOUT";
        }

        String key = keyPrefix + ":ip:" + ip;
        return limiter.check(key);
    }

    private String extractClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "unknown" : remote;
    }
}
