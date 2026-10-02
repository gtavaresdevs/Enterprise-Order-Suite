package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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

// Pinned: spring-dotenv loads the developer's .env, which sets REFRESH_COOKIE_SECURE=false
// locally. These assertions describe what production ships, on every machine.
// MockMvc has no context path, so the cookie path is /auth here; /api/auth is unit-tested.
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.refresh-cookie.secure=true",
  "security.refresh-cookie.same-site=Lax",
  "security.cors.allowed-origins=http://localhost:3000"
})
class RefreshCookieIT {

  private static final String PASSWORD = "Password123!";
  private static final String FRONTEND = "http://localhost:3000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void login_setsTheRefreshCookie_matchingTheBodyToken() throws Exception {
    MvcResult result = login(register());

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
      .startsWith("refreshToken=")
      .contains("Path=/auth", "Max-Age=1209600", "HttpOnly", "Secure", "SameSite=Lax");
    assertThat(cookieValue(result)).isEqualTo(bodyRefreshToken(result));
  }

  @Test
  void login_underApiContextPath_setsTheRefreshCookieScopedToApiAuth() throws Exception {
    RegisterRequest request = registerRequest();
    mockMvc.perform(post(URI.create("/api/auth/register")).contextPath("/api")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(post(URI.create("/api/auth/login")).contextPath("/api")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(request.getEmail(), PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
      .as("cookie path must include the deployed context path so it is only sent to /api/auth/*")
      .startsWith("refreshToken=")
      .contains("Path=/api/auth");
  }

  @Test
  void register_setsTheRefreshCookie() throws Exception {
    RegisterRequest request = registerRequest();
    MvcResult result = mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(cookieValue(result)).isNotBlank().isEqualTo(bodyRefreshToken(result));
  }

  @Test
  void refresh_viaCookieWithNoBody_rotatesAndSetsANewCookie() throws Exception {
    String first = cookieValue(login(register()));

    MvcResult result = mockMvc.perform(post("/auth/refresh")
        .cookie(new Cookie("refreshToken", first))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andReturn();

    assertThat(cookieValue(result)).isNotBlank().isNotEqualTo(first);
  }

  @Test
  void refresh_cookieAndBodyBothPresent_cookieWins() throws Exception {
    String token = cookieValue(login(register()));

    mockMvc.perform(post("/auth/refresh")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest("stale-local-storage-value"))))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_jsonContentTypeButNoTokenAnywhere_returns401() throws Exception {
    mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logout_viaCookie_clearsTheCookie_andRevokesTheFamily() throws Exception {
    String token = cookieValue(login(register()));

    MvcResult result = mockMvc.perform(post("/auth/logout")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
      .startsWith("refreshToken=;")
      .contains("Max-Age=0", "Path=/auth", "HttpOnly", "Secure", "SameSite=Lax");

    mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(token))))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_withNoTokenAnywhere_isIdempotent_andStillClearsTheCookie() throws Exception {
    MvcResult result = mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setFirstName("Cookie");
    request.setLastName("Jar");
    request.setEmail("cookie-" + UUID.randomUUID() + "@test.com");
    request.setPassword(PASSWORD);
    return request;
  }

  private String register() throws Exception {
    RegisterRequest request = registerRequest();
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());
    return request.getEmail();
  }

  private MvcResult login(String email) throws Exception {
    return mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();
  }

  private String cookieValue(MvcResult result) {
    String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(header).as("response must set the refresh cookie").isNotNull();
    return header.substring("refreshToken=".length(), header.indexOf(';'));
  }

  private String bodyRefreshToken(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();
  }
}
