package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.security.ratelimit.RateLimitDecision;
import com.enterprise.ordersuite.security.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Slf4j
// ENTERPRISE REFACTOR: Explicitly define the execution order position.
// Ordered.HIGHEST_PRECEDENCE + 1 ensures this executes immediately AFTER the RequestIdFilter,
// guaranteeing that 429 exceptions are fully logged with a structured tracking UUID.
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private final RateLimiter forgotPasswordLimiter;
  private final RateLimiter loginLimiter;
  private final RateLimiter resetPasswordLimiter;
  private final RateLimiter refreshLimiter;
  private final RateLimiter logoutLimiter;

  private final ObjectMapper objectMapper;
  private final Clock clock;

  // Enterprise standard: Auth payloads should comfortably fit within 8KB.
  // This acts as a guard clause against memory exhaustion attacks.
  private static final int MAX_AUTH_PAYLOAD_SIZE_BYTES = 8192;

  public AuthRateLimitFilter(
    RateLimiter forgotPasswordLimiter,
    RateLimiter loginLimiter,
    RateLimiter resetPasswordRateLimiter,
    RateLimiter refreshLimiter,
    RateLimiter logoutLimiter,
    ObjectMapper objectMapper,
    Clock clock
  ) {
    this.forgotPasswordLimiter = forgotPasswordLimiter;
    this.loginLimiter = loginLimiter;
    this.resetPasswordLimiter = resetPasswordRateLimiter;
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

    // 1. Guard Clause: Protect against large payloads targeting JVM Heap (DoS Defense)
    int contentLength = request.getContentLength();
    if (contentLength > MAX_AUTH_PAYLOAD_SIZE_BYTES) {
      log.warn("Rejected oversized auth payload ({} bytes) from client remote address: {}",
        contentLength, request.getRemoteAddr());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", "Payload size exceeds maximum allowed limit.");
      return;
    }

    // 2. Safely wrap request to allow multiple downstream stream reads
    CachedBodyRequestWrapper wrappedRequest = new CachedBodyRequestWrapper(request);
    String path = wrappedRequest.getRequestURI();
    String email = null;

    // 3. Extract target account identifiers for body-dependent rate limiting
    if (path.equals("/auth/forgot-password") || path.equals("/auth/login")) {
      try {
        String requestBody = new String(wrappedRequest.getCachedBody(),
          wrappedRequest.getCharacterEncoding() != null ? wrappedRequest.getCharacterEncoding() : "UTF-8");

        if (!requestBody.isEmpty()) {
          JsonNode jsonNode = objectMapper.readTree(requestBody);
          if (jsonNode.has("email")) {
            email = jsonNode.get("email").asText();
          }
        }
      } catch (Exception e) {
        log.warn("Could not parse email identifier from body for path: {} from client: {}", path, wrappedRequest.getRemoteAddr(), e);
      }
    }

    // 4. Relying on remote address (delegating X-Forwarded-For parsing security to Tomcat RemoteIpFilter)
    String ip = wrappedRequest.getRemoteAddr();

    // 5. Check and apply dual-key rate limit configurations
    RateLimitDecision decision = checkRateLimit(path, ip, email);

    if (decision.allowed()) {
      filterChain.doFilter(wrappedRequest, response);
      return;
    }

    // Because RequestIdFilter executed first, this warning log will inherit
    // the MDC "requestId" context seamlessly!
    log.warn("Rate limit triggered for path: {} [IP: {}, Account Identification: {}]", path, ip, email != null ? email : "N/A");

    // 6. Handle rate-limited requests
    sendErrorResponse(response, 429, "RATE_LIMITED", "Too many requests. Please try again later.", decision.retryAfterSeconds());
  }

  private RateLimitDecision checkRateLimit(String path, String ip, String email) {
    if (path.equals("/auth/forgot-password")) {
      String key = "FORGOT_PASSWORD:ip:" + ip + (email != null ? ":email:" + email : "");
      return forgotPasswordLimiter.check(key);
    }

    if (path.equals("/auth/login")) {
      // Dual-Key Strategy: Check client IP threshold first
      RateLimitDecision ipDecision = loginLimiter.check("LOGIN:ip:" + ip);
      if (!ipDecision.allowed()) {
        return ipDecision;
      }
      // Check targeted account threshold second to block Distributed Credential Stuffing
      if (email != null) {
        RateLimitDecision emailDecision = loginLimiter.check("LOGIN:email:" + email);
        if (!emailDecision.allowed()) {
          return emailDecision;
        }
      }
      return ipDecision;
    }

    if (path.equals("/auth/reset-password")) {
      return resetPasswordLimiter.check("RESET_PASSWORD:ip:" + ip);
    }

    if (path.equals("/auth/refresh")) {
      return refreshLimiter.check("REFRESH:ip:" + ip);
    }

    // Default mapping: Logout
    return logoutLimiter.check("LOGOUT:ip:" + ip);
  }

  private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorCode, String message) throws IOException {
    sendErrorResponse(response, statusCode, errorCode, message, 0);
  }

  private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorCode, String message, long retryAfterSeconds) throws IOException {
    response.setStatus(statusCode);
    if (retryAfterSeconds > 0) {
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ApiErrorResponse body = new ApiErrorResponse(
      errorCode,
      message,
      Instant.now(clock)
    );

    objectMapper.writeValue(response.getOutputStream(), body);
  }

  private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
      super(request);
      this.cachedBody = request.getInputStream().readAllBytes();
    }

    public byte[] getCachedBody() {
      return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
      return new ServletInputStream() {
        @Override
        public int read() { return bais.read(); }
        @Override
        public boolean isFinished() { return bais.available() == 0; }
        @Override
        public boolean isReady() { return true; }
        @Override
        public void setReadListener(ReadListener readListener) {}
      };
    }
  }
}
