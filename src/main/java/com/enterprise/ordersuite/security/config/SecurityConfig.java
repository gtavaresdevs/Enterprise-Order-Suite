package com.enterprise.ordersuite.security.config;

import com.enterprise.ordersuite.security.jwt.JwtAuthenticationFilter;
import com.enterprise.ordersuite.security.ratelimit.InMemoryBucketedSlidingWindowRateLimiter;
import com.enterprise.ordersuite.security.ratelimit.RateLimiter;
import com.enterprise.ordersuite.security.web.AuthRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
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
    public RateLimiter refreshLimiter () {
        return new InMemoryBucketedSlidingWindowRateLimiter(10, 1, clock);
    }

    @Bean("logoutLimiter")
    public RateLimiter logoutLimiter () {
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
            AuthRateLimitFilter authRateLimitFilter
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/refresh",
                                "/auth/logout"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authenticationProvider(authenticationProvider)

                // Rate limit FIRST
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // JWT auth AFTER
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
