package com.enterprise.ordersuite.security.config;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.security.jwt.JwtAuthenticationFilter;
import com.enterprise.ordersuite.security.ratelimit.InMemoryBucketedSlidingWindowRateLimiter;
import com.enterprise.ordersuite.security.ratelimit.RateLimiter;
import com.enterprise.ordersuite.security.web.AuthRateLimitFilter;
import com.enterprise.ordersuite.security.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;
import java.time.Instant;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ---- Rate limiters ----

    @Bean("forgotPasswordRateLimiter")
    public RateLimiter forgotPasswordRateLimiter() {
        return new InMemoryBucketedSlidingWindowRateLimiter(5, 10, clock);
    }

    @Bean("loginRateLimiter")
    public RateLimiter loginRateLimiter() {
        return new InMemoryBucketedSlidingWindowRateLimiter(5, 5, clock);
    }

    @Bean("refreshLimiter")
    public RateLimiter refreshLimiter() {
        return new InMemoryBucketedSlidingWindowRateLimiter(10, 1, clock);
    }

    @Bean("logoutLimiter")
    public RateLimiter logoutLimiter() {
        return new InMemoryBucketedSlidingWindowRateLimiter(30, 1, clock);
    }

    @Bean("resetPasswordRateLimiter")
    public RateLimiter resetPasswordRateLimiter() {
        return new InMemoryBucketedSlidingWindowRateLimiter(5, 10, clock);
    }

    @Bean
    public AuthRateLimitFilter authRateLimitFilter(
            RateLimiter forgotPasswordRateLimiter,
            RateLimiter loginRateLimiter,
            RateLimiter resetPasswordRateLimiter,
            RateLimiter refreshLimiter,
            RateLimiter logoutLimiter
    ) {
        return new AuthRateLimitFilter(
                forgotPasswordRateLimiter,
                loginRateLimiter,
                resetPasswordRateLimiter,
                refreshLimiter,
                logoutLimiter,
                objectMapper,
                clock
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthRateLimitFilter authRateLimitFilter,
            RequestIdFilter requestIdFilter
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/auth/**",
                                "/actuator/health",
                                "/actuator/metrics/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Admin-only endpoints must be blocked at the security layer
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers("/roles").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authenticationProvider(authenticationProvider)

                // RequestId FIRST
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)

                // Rate limit
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // JWT auth
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiErrorResponse body = new ApiErrorResponse(
                    "UNAUTHORIZED",
                    "Authentication required",
                    Instant.now(clock)
            );

            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }
}