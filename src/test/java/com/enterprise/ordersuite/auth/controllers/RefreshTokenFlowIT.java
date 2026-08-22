package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class RefreshTokenFlowIT {

  private static final String RAW_PASSWORD = "Password123!";
  private static final String TEST_IP = "10.10.10.10";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private String testEmail;

  @BeforeEach
  void setUp() throws Exception {
    testEmail = "testuser_" + UUID.randomUUID() + "@example.com";

    // Register test user with all mandatory fields via API
    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setFirstName("Test");
    registerRequest.setLastName("User");
    registerRequest.setEmail(testEmail);
    registerRequest.setPassword(RAW_PASSWORD);

    mockMvc.perform(
        post("/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(registerRequest))
      )
      .andExpect(status().isOk());
  }

  @Test
  void login_then_refresh_rotates_and_old_token_fails_and_logout_revokes() throws Exception {
    // 1. Initial Login
    AuthRequest loginRequest = new AuthRequest(testEmail, RAW_PASSWORD);

    MvcResult loginResult = mockMvc.perform(
        post("/auth/login")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(loginRequest))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andExpect(jsonPath("$.refreshToken").isNotEmpty())
      .andReturn();

    String refreshToken = extractToken(loginResult, "refreshToken");

    // 2. First Refresh -> Obtains new refresh token
    MvcResult firstRefreshResult = mockMvc.perform(
        post("/auth/refresh")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new RefreshRequest(refreshToken))
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andExpect(jsonPath("$.refreshToken").isNotEmpty())
      .andReturn();

    String newRefreshToken = extractToken(firstRefreshResult, "refreshToken");

    assertThat(newRefreshToken)
      .isNotBlank()
      .isNotEqualTo(refreshToken);

    // 3. Attempt Refresh using rotated/old token -> Fails with 401
    mockMvc.perform(
        post("/auth/refresh")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new RefreshRequest(refreshToken))
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // 4. Second Refresh using valid rotated token
    MvcResult secondRefreshResult = mockMvc.perform(
        post("/auth/refresh")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new RefreshRequest(newRefreshToken))
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andExpect(jsonPath("$.refreshToken").isNotEmpty())
      .andReturn();

    String newestRefreshToken = extractToken(secondRefreshResult, "refreshToken");

    assertThat(newestRefreshToken)
      .isNotBlank()
      .isNotEqualTo(newRefreshToken)
      .isNotEqualTo(refreshToken);

    // 5. Logout using current active refresh token
    mockMvc.perform(
        post("/auth/logout")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new LogoutRequest(newestRefreshToken))
          )
      )
      .andExpect(status().isOk());

    // 6. Attempt Refresh with logged-out token -> Fails with 401
    mockMvc.perform(
        post("/auth/refresh")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new LogoutRequest(newestRefreshToken))
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // 7. Idempotent Logout check
    mockMvc.perform(
        post("/auth/logout")
          .with(request -> {
            request.setRemoteAddr(TEST_IP);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(new LogoutRequest(newestRefreshToken))
          )
      )
      .andExpect(status().isOk());
  }

  private String extractToken(MvcResult result, String fieldName) throws Exception {
    JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
    return responseNode.get(fieldName).asText();
  }
}
