package com.enterprise.ordersuite.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
// Enterprise Standard: Enforce this filter to execute at the very top of the Servlet pipeline.
// Tracing identifiers must be captured BEFORE any authentication or rate limit layers run.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  // Strict validation regex: Allows standard UUIDs, alphanumeric string keys, and hyphens.
  // Blocks carriage returns, line feeds, and scripting elements to defend against Log Injection.
  private static final Pattern VALID_REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]+$");
  private static final int MAX_REQUEST_ID_LENGTH = 64;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String incomingHeader = request.getHeader(HEADER_NAME);
    String requestId = sanitizeOrGenerateRequestId(incomingHeader);

    // Populate logging diagnostics context and standard HTTP tracking response headers
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER_NAME, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Guarantee resource drainage and prevent thread-local leakage in pooled worker systems
      MDC.remove(MDC_KEY);
    }
  }

  private String sanitizeOrGenerateRequestId(String incoming) {
    if (incoming == null || incoming.isBlank()) {
      return generateSecureUniqueId();
    }

    String trimmed = incoming.trim();

    // Guard against overly large headers targeting heap memory or log layouts
    if (trimmed.length() > MAX_REQUEST_ID_LENGTH) {
      return generateSecureUniqueId();
    }

    // Validate structure format tightly. If invalid, safely fall back to a fresh sequence.
    if (!VALID_REQUEST_ID_PATTERN.matcher(trimmed).matches()) {
      return generateSecureUniqueId();
    }

    return trimmed;
  }

  private String generateSecureUniqueId() {
    return UUID.randomUUID().toString();
  }
}
