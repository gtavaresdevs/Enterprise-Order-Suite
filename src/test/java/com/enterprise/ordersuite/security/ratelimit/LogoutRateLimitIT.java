package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.logout.capacity=3",
  "security.rate-limit.logout.refill-seconds=10"
})
class LogoutRateLimitIT {

  private static final int RATE_LIMIT_CAPACITY = 3;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should allow logout requests up to configured capacity and then return 429")
  void logout_enforcesConfiguredCapacity() throws Exception {
    String ip = "10.10.10.98";
    String refreshToken = authenticateTestUser(ip);

    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performLogout(ip, refreshToken);

      assertThat(result.getResponse().getStatus())
        .as(
          "Logout attempt %d should be allowed before the configured rate-limit capacity is exceeded",
          attempt
        )
        .isEqualTo(200);
    }

    MvcResult rateLimitedResult = performLogout(ip, refreshToken);

    assertThat(rateLimitedResult.getResponse().getStatus())
      .as("The request exceeding the configured logout capacity should be rate limited")
      .isEqualTo(429);

    verifyRateLimitResponse(rateLimitedResult);
  }

  private String authenticateTestUser(String ip) throws Exception {
    String email = uniqueEmail();
    String password = "SecurePass123!@#";

    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setFirstName("Logout");
    registerRequest.setLastName("RateLimit");
    registerRequest.setEmail(email);
    registerRequest.setPassword(password);

    mockMvc.perform(post("/auth/register")
        .with(request -> {
          request.setRemoteAddr(ip);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(registerRequest)))
      .andExpect(status().isOk());

    AuthRequest loginRequest = new AuthRequest(email, password);

    MvcResult loginResult = mockMvc.perform(post("/auth/login")
        .with(request -> {
          request.setRemoteAddr(ip);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isOk())
      .andReturn();

    JsonNode response = objectMapper.readTree(
      loginResult.getResponse().getContentAsString()
    );

    assertThat(response.hasNonNull("refreshToken"))
      .as("Successful authentication must return a refresh token")
      .isTrue();

    return response.get("refreshToken").asText();
  }

  private MvcResult performLogout(String ip, String refreshToken) throws Exception {
    LogoutRequest logoutRequest = new LogoutRequest(refreshToken);

    return mockMvc.perform(post("/auth/logout")
        .with(request -> {
          request.setRemoteAddr(ip);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(logoutRequest)))
      .andReturn();
  }

  private void verifyRateLimitResponse(MvcResult result) throws Exception {
    assertThat(result.getResponse().getHeader("Retry-After"))
      .as("Rate-limited logout responses must provide a Retry-After header")
      .isNotBlank();

    long retryAfter = Long.parseLong(
      result.getResponse().getHeader("Retry-After")
    );

    assertThat(retryAfter)
      .as("Retry-After must contain a positive number of seconds")
      .isPositive();

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );

    assertThat(error.code()).isEqualTo("RATE_LIMITED");
    assertThat(error.message())
      .isEqualTo("Too many requests. Please try again later.");
    assertThat(error.timestamp()).isNotNull();
  }

  private String uniqueEmail() {
    return "logout-ratelimit-" + UUID.randomUUID() + "@test.com";
  }
}
