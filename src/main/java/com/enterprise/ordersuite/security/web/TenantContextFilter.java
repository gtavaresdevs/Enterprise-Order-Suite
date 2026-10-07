package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.common.tenancy.TenantContext;
import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Sets the request's TenantContext from the access token (Tenancy & Identity §5.1), after
 * JwtAuthenticationFilter. A member's restaurant comes only from the token, never from the
 * request (D-6). The support header is read only for a platform admin's GET (§5.4, D-15);
 * on any other method or from any other role it is 403, so it cannot widen a member's reach.
 * Anonymous requests get no context.
 */
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String SUPPORT_HEADER = "X-Support-Restaurant-Id";
    public static final String MDC_RESTAURANT_ID = "restaurantId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_SUPPORT = "support";

    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final Pattern ULID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TenantContextFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        TenantContext context;
        String supportRestaurantId = request.getHeader(SUPPORT_HEADER);
        if (supportRestaurantId == null) {
            context = new TenantContext(principal.restaurantId(), principal.id(), principal.role(), false);
        } else if (!PLATFORM_ADMIN.equals(principal.role()) || !"GET".equals(request.getMethod())
                || !ULID.matcher(supportRestaurantId).matches()) {
            forbid(response);
            return;
        } else {
            context = new TenantContext(supportRestaurantId, principal.id(), PLATFORM_ADMIN, true);
        }

        TenantContextHolder.set(context);
        MDC.put(MDC_USER_ID, context.userId());
        if (context.restaurantId() != null) {
            MDC.put(MDC_RESTAURANT_ID, context.restaurantId());
        }
        if (context.support()) {
            // Q-18 b: support reads are logged, not written to the audit log.
            MDC.put(MDC_SUPPORT, "true");
            log.info("Support read {} {}", request.getMethod(), RequestPaths.withinApplication(request));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_RESTAURANT_ID);
            MDC.remove(MDC_SUPPORT);
        }
    }

    private void forbid(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse("FORBIDDEN", "Support header not allowed", Instant.now(clock)));
    }
}
