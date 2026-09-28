package com.enterprise.ordersuite.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshOriginFilterTest {

  private static final String FRONTEND = "http://localhost:3000";

  private final RefreshOriginFilter filter = new RefreshOriginFilter(
    List.of(FRONTEND),
    new ObjectMapper().registerModule(new JavaTimeModule()),
    Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void cookieWithAllowedOrigin_passes() throws Exception {
    MockHttpServletResponse response = run(request("/auth/refresh", true, FRONTEND));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void cookieWithoutOrigin_isRejected() throws Exception {
    MockHttpServletResponse response = run(request("/auth/refresh", true, null));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"ORIGIN_NOT_ALLOWED\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evil.example", "http://localhost:3000/", "http://localhost:3001", "null"})
  void cookieWithAnyOtherOrigin_isRejected(String origin) throws Exception {
    assertThat(run(request("/auth/logout", true, origin)).getStatus())
      .as("exact match only - '%s' is not the frontend", origin)
      .isEqualTo(403);
  }

  @Test
  void noCookie_skipsTheCheck_evenWithAForeignOrigin() throws Exception {
    assertThat(run(request("/auth/refresh", false, "https://evil.example")).getStatus())
      .as("a body-borne token was already readable by the caller, so CSRF does not apply")
      .isEqualTo(200);
  }

  @Test
  void otherAuthEndpoints_areNotGuarded() throws Exception {
    assertThat(run(request("/auth/login", true, "https://evil.example")).getStatus()).isEqualTo(200);
  }

  @Test
  void guardedPath_isMatchedUnderAContextPath() throws Exception {
    MockHttpServletRequest request = request("/api/auth/refresh", true, null);
    request.setContextPath("/api");

    assertThat(run(request).getStatus())
      .as("production serves under /api; the guard must not silently switch off there")
      .isEqualTo(403);
  }

  private MockHttpServletRequest request(String uri, boolean withCookie, String origin) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    if (withCookie) {
      request.setCookies(new Cookie("refreshToken", "raw"));
    }
    if (origin != null) {
      request.addHeader("Origin", origin);
    }
    return request;
  }

  private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
