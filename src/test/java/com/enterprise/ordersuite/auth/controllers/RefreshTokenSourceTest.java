package com.enterprise.ordersuite.auth.controllers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenSourceTest {

  @Test
  void resolve_cookieOnly_returnsCookie() {
    assertThat(RefreshTokenSource.resolve("from-cookie", null)).isEqualTo("from-cookie");
  }

  @Test
  void resolve_bodyOnly_returnsBody() {
    assertThat(RefreshTokenSource.resolve(null, "from-body")).isEqualTo("from-body");
  }

  @Test
  void resolve_both_cookieWins() {
    assertThat(RefreshTokenSource.resolve("from-cookie", "from-body"))
      .as("a stale localStorage value in a half-migrated frontend must not override the fresh cookie")
      .isEqualTo("from-cookie");
  }

  @Test
  void resolve_blankCookie_fallsBackToBody() {
    assertThat(RefreshTokenSource.resolve("", "from-body")).isEqualTo("from-body");
  }

  @Test
  void resolve_neither_returnsNull() {
    assertThat(RefreshTokenSource.resolve(null, null)).isNull();
    assertThat(RefreshTokenSource.resolve(" ", "")).isNull();
  }
}
