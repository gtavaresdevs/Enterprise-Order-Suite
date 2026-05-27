package com.enterprise.ordersuite.security.jwt;

import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // If no token or token does not start with Bearer, skip filter
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtService.isTokenValid(jwt)) {
                String userEmail = jwtService.extractEmail(jwt);
                Long userId = jwtService.extractClaim(jwt, claims -> ((Number) claims.get("userId")).longValue());
                List<String> roles = jwtService.extractRoles(jwt);

                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                    // Map roles to Spring Security authorities with "ROLE_" prefix
                    if (roles != null) {
                        authorities.addAll(roles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .collect(Collectors.toList()));
                    }

                    // Map roles to scopes for PreAuthorize compatibility
                    // This assumes that if a user has a certain role, they also have corresponding scopes.
                    // In a real application, scopes might be managed separately.
                    if (roles != null && roles.contains("ADMIN")) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_order:write"));
                        authorities.add(new SimpleGrantedAuthority("SCOPE_order:read"));
                        authorities.add(new SimpleGrantedAuthority("SCOPE_order:delete"));
                    } else if (roles != null && roles.contains("USER")) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_order:read"));
                        // A regular user might have write access to their own orders, but not all orders.
                        // This is handled by @PostAuthorize or programmatic checks.
                        authorities.add(new SimpleGrantedAuthority("SCOPE_order:write"));
                    }


                    JwtUserPrincipal principal = new JwtUserPrincipal(userId, userEmail);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    authorities
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("User {} (ID: {}) authenticated with authorities: {}", userEmail, userId, authorities);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to authenticate user from JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
