package com.enterprise.ordersuite.security;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.refresh-cookie.secure=true",
  "security.refresh-cookie.same-site=Lax",
  "security.cors.allowed-origins=http://localhost:3000"
})
class RefreshOriginIT {

  private static final String FRONTEND = "http://localhost:3000";
  private static final String EVIL = "https://evil.example";
  private static final String PASSWORD = "Password123!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void refresh_cookieWithoutOrigin_returns403_andDoesNotConsumeTheToken() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(cookieRefresh(token))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_cookieFromAForeignOrigin_returns403_andDoesNotConsumeTheToken() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, EVIL))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void logout_cookieFromAForeignOrigin_returns403_andLeavesTheSessionAlive() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(post("/auth/logout")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, EVIL)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isForbidden());

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_percentEncodedPathWithCookieFromAForeignOrigin_returns403_andDoesNotConsumeTheToken() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(post(URI.create("/api/auth/%72efresh"))
        .contextPath("/api")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, EVIL)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_bodyTokenWithoutOrigin_stillWorks() throws Exception {
    mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(loginForRefreshToken()))))
      .andExpect(status().isOk());
  }

  @Test
  void preflight_fromTheFrontend_allowsCredentials() throws Exception {
    mockMvc.perform(options("/auth/refresh")
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
      .andExpect(status().isOk())
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  private MockHttpServletRequestBuilder cookieRefresh(String token) {
    return post("/auth/refresh")
      .cookie(new Cookie("refreshToken", token))
      .contentType(MediaType.APPLICATION_JSON);
  }

  private String loginForRefreshToken() throws Exception {
    RegisterRequest register = new RegisterRequest();
    register.setFirstName("Origin");
    register.setLastName("Check");
    register.setEmail("origin-" + UUID.randomUUID() + "@test.com");
    register.setPassword(PASSWORD);
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
      .andExpect(status().isOk());

    String body = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(register.getEmail(), PASSWORD))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("refreshToken").asText();
  }
}
