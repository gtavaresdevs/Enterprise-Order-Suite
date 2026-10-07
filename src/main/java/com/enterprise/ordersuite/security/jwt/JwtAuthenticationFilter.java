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
import java.util.List;

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
                String userId = jwtService.extractUserId(jwt);
                String restaurantId = jwtService.extractRestaurantId(jwt);
                String role = jwtService.extractRole(jwt);

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // The role claim becomes the only authority; RoleHierarchy expands it
                    // for @PreAuthorize.
                    List<SimpleGrantedAuthority> authorities = role == null
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));

                    JwtUserPrincipal principal = new JwtUserPrincipal(userId, restaurantId, role);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    authorities
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("User {} authenticated with authorities: {}", userId, authorities);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to authenticate user from JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
