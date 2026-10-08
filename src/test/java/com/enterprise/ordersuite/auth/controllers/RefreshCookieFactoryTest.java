package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {

  @Test
  void issue_carriesEveryAttributeTheContractNames() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(true, "Lax"));

    ResponseCookie cookie = factory.issue("raw", "/api");

    assertThat(cookie.getName()).isEqualTo("refreshToken");
    assertThat(cookie.getValue()).isEqualTo("raw");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
    assertThat(cookie.getPath())
      .as("production serves under context path /api; the cookie must reach /api/auth/refresh")
      .isEqualTo("/api/auth");
    assertThat(cookie.getMaxAge()).as("30 days, sliding (Tenancy & Identity D-11)").isEqualTo(Duration.ofDays(30));
  }

  @Test
  void issue_followsProperties_forLocalHttpDevelopment() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(false, "Strict"));

    ResponseCookie cookie = factory.issue("raw", "");

    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/auth");
  }

  @Test
  void clear_mirrorsTheIssuedCookie_withZeroMaxAge() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(true, "Lax"));

    ResponseCookie cookie = factory.clear("/api");

    assertThat(cookie.getName()).isEqualTo("refreshToken");
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cookie.getPath()).as("a browser only clears a cookie whose path matches").isEqualTo("/api/auth");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }
}
