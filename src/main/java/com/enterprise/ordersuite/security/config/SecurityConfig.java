package com.enterprise.ordersuite.security.config;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.security.jwt.JwtAuthenticationFilter;
import com.enterprise.ordersuite.security.ratelimit.InMemoryBucketedSlidingWindowRateLimiter;
import com.enterprise.ordersuite.security.ratelimit.NoOpRateLimiter;
import com.enterprise.ordersuite.security.ratelimit.RateLimiter;
import com.enterprise.ordersuite.security.web.AuthRateLimitFilter;
import com.enterprise.ordersuite.security.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final RateLimitProperties properties;

  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN \n ROLE_ADMIN > ROLE_USER");
  }

  @Bean
  static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
    DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
    expressionHandler.setRoleHierarchy(roleHierarchy);
    return expressionHandler;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  // ---- Rate Limiters (Omitted existing logic for brevity) ----

  @Bean("forgotPasswordRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter forgotPasswordRateLimiter() {
    var config = properties.forgotPassword();
    return new InMemoryBucketedSlidingWindowRateLimiter(config.capacity(), config.refillSeconds(), clock);
  }

  @Bean("loginRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter loginRateLimiter() {
    var config = properties.login();
    return new InMemoryBucketedSlidingWindowRateLimiter(config.capacity(), config.refillSeconds(), clock);
  }

  @Bean("refreshLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter refreshLimiter() {
    var config = properties.refresh();
    return new InMemoryBucketedSlidingWindowRateLimiter(config.capacity(), config.refillSeconds(), clock);
  }

  @Bean("logoutLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter logoutLimiter() {
    var config = properties.logout();
    return new InMemoryBucketedSlidingWindowRateLimiter(config.capacity(), config.refillSeconds(), clock);
  }

  @Bean("resetPasswordRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter resetPasswordRateLimiter() {
    var config = properties.resetPassword();
    return new InMemoryBucketedSlidingWindowRateLimiter(config.capacity(), config.refillSeconds(), clock);
  }

  @Bean("noOpForgotPasswordRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "false")
  public RateLimiter noOpForgotPasswordRateLimiter() { return new NoOpRateLimiter(); }

  @Bean("noOpLoginRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "false")
  public RateLimiter noOpLoginRateLimiter() { return new NoOpRateLimiter(); }

  @Bean("noOpRefreshLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "false")
  public RateLimiter noOpRefreshLimiter() { return new NoOpRateLimiter(); }

  @Bean("noOpLogoutLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "false")
  public RateLimiter noOpLogoutLimiter() { return new NoOpRateLimiter(); }

  @Bean("noOpResetPasswordRateLimiter")
  @ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "false")
  public RateLimiter noOpResetPasswordRateLimiter() { return new NoOpRateLimiter(); }

  @Bean
  public AuthRateLimitFilter authRateLimitFilter(
    @Qualifier("forgotPasswordRateLimiter") Optional<RateLimiter> forgotPasswordLimiter,
    @Qualifier("loginRateLimiter") Optional<RateLimiter> loginLimiter,
    @Qualifier("resetPasswordRateLimiter") Optional<RateLimiter> resetPasswordRateLimiter,
    @Qualifier("refreshLimiter") Optional<RateLimiter> refreshLimiter,
    @Qualifier("logoutLimiter") Optional<RateLimiter> logoutLimiter,
    @Qualifier("noOpForgotPasswordRateLimiter") Optional<RateLimiter> noOpForgotPasswordLimiter,
    @Qualifier("noOpLoginRateLimiter") Optional<RateLimiter> noOpLoginLimiter,
    @Qualifier("noOpResetPasswordRateLimiter") Optional<RateLimiter> noOpResetPasswordLimiter,
    @Qualifier("noOpRefreshLimiter") Optional<RateLimiter> noOpRefreshLimiter,
    @Qualifier("noOpLogoutLimiter") Optional<RateLimiter> noOpLogoutLimiter
  ) {
    return new AuthRateLimitFilter(
      forgotPasswordLimiter.orElseGet(noOpForgotPasswordLimiter::get),
      loginLimiter.orElseGet(noOpLoginLimiter::get),
      resetPasswordRateLimiter.orElseGet(noOpResetPasswordLimiter::get),
      refreshLimiter.orElseGet(noOpRefreshLimiter::get),
      logoutLimiter.orElseGet(noOpLogoutLimiter::get),
      objectMapper,
      clock
    );
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    Optional<AuthRateLimitFilter> authRateLimitFilter,
    RequestIdFilter requestIdFilter
  ) throws Exception {

    http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(AbstractHttpConfigurer::disable)
      .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/error", "/auth/**", "/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/webjars/**", "/logo.png").permitAll()
        .requestMatchers("/actuator/info", "/actuator/metrics/**").hasRole("SUPER_ADMIN")
        .requestMatchers("/admin/users/**", "/admin/identity-audit/**").hasRole("SUPER_ADMIN")
        .requestMatchers("/admin/**", "/roles").hasRole("ADMIN")
        .anyRequest().authenticated()
      )
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
      .addFilterBefore(authRateLimitFilter.orElse(null), UsernamePasswordAuthenticationFilter.class)
      .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      ApiErrorResponse body = new ApiErrorResponse("UNAUTHORIZED", "Authentication required", Instant.now(clock));
      objectMapper.writeValue(response.getOutputStream(), body);
    };
  }
}
