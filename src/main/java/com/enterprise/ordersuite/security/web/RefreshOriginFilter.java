package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
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
import java.util.List;
import java.util.Set;

/**
 * CSRF defense for the only two cookie-authenticated endpoints (D19). Every request to them
 * must come from an allowed origin, with or without the cookie (Tenancy & Identity D-10):
 * the refresh token travels only in the cookie, so these endpoints have no other caller.
 * Not authorization: it never looks at who the user is.
 */
public class RefreshOriginFilter extends OncePerRequestFilter {

    private static final Set<String> GUARDED_PATHS = Set.of("/auth/refresh", "/auth/logout");

    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RefreshOriginFilter(List<String> allowedOrigins, ObjectMapper objectMapper, Clock clock) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GUARDED_PATHS.contains(RequestPaths.withinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse("ORIGIN_NOT_ALLOWED", "Origin not allowed", Instant.now(clock)));
    }
}
