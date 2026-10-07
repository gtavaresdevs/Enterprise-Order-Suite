package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.RefreshCookies;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pinned: a developer's local environment may set REFRESH_COOKIE_SECURE=false. These
// assertions describe what production ships, on every machine.
// MockMvc has no context path, so the cookie path is /auth here; /api/auth is tested below.
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.refresh-cookie.secure=true",
  "security.refresh-cookie.same-site=Lax",
  "security.cors.allowed-origins=http://localhost:3000"
})
class RefreshCookieIT {

  private static final String PASSWORD = "Password123!";
  // 30 days, sliding (Tenancy & Identity D-11).
  private static final String THIRTY_DAYS = "Max-Age=2592000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Test
  void login_setsThe30DayRefreshCookie_andTheBodyHasOnlyTheAccessToken() throws Exception {
    MvcResult result = login(register());

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
      .startsWith("refreshToken=")
      .contains("Path=/auth", THIRTY_DAYS, "HttpOnly", "Secure", "SameSite=Lax");
    assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).fieldNames())
      .toIterable()
      .as("acceptance 11: no auth response body contains refreshToken")
      .containsExactly("accessToken");
  }

  @Test
  void login_underApiContextPath_setsTheRefreshCookieScopedToApiAuth() throws Exception {
    String email = register();

    MvcResult result = mockMvc.perform(post(URI.create("/api/auth/login")).contextPath("/api")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
      .as("cookie path must include the deployed context path so it is only sent to /api/auth/*")
      .startsWith("refreshToken=")
      .contains("Path=/api/auth");
  }

  @Test
  void refresh_viaCookieWithNoBodyAndNoContentType_rotates_andSlidesTheExpiry() throws Exception {
    String first = RefreshCookies.valueOf(login(register()));

    MvcResult result = mockMvc.perform(RefreshCookies.refresh(first))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andExpect(jsonPath("$.refreshToken").doesNotExist())
      .andReturn();

    assertThat(RefreshCookies.valueOf(result)).isNotBlank().isNotEqualTo(first);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
      .as("each rotation sets a new 30-day expiry")
      .contains(THIRTY_DAYS);
  }

  @Test
  void refresh_withAJsonContentType_stillWorks() throws Exception {
    String token = RefreshCookies.valueOf(login(register()));

    mockMvc.perform(RefreshCookies.refresh(token).contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_tokenInTheBody_isIgnored_andIs401() throws Exception {
    String token = RefreshCookies.valueOf(login(register()));

    mockMvc.perform(post("/auth/refresh")
        .header(HttpHeaders.ORIGIN, RefreshCookies.FRONTEND)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"refreshToken\":\"" + token + "\"}"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // The ignored body did not consume the token.
    mockMvc.perform(RefreshCookies.refresh(token))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_noCookie_returns401() throws Exception {
    mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, RefreshCookies.FRONTEND))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logout_viaCookie_clearsTheCookie_andRevokesTheFamily() throws Exception {
    String token = RefreshCookies.valueOf(login(register()));

    MvcResult result = mockMvc.perform(RefreshCookies.logout(token))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
      .startsWith("refreshToken=;")
      .contains("Max-Age=0", "Path=/auth", "HttpOnly", "Secure", "SameSite=Lax");

    mockMvc.perform(RefreshCookies.refresh(token))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_withNoCookie_isIdempotent_andStillClearsTheCookie() throws Exception {
    MvcResult result = mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, RefreshCookies.FRONTEND))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  private String register() {
    String email = "cookie-" + UUID.randomUUID() + "@test.com";
    testUsers.owner(email, PASSWORD);
    return email;
  }

  private MvcResult login(String email) throws Exception {
    return mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();
  }
}
