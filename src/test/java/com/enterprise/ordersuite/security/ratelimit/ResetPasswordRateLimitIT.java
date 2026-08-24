package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.reset-password.capacity=3",
  "security.rate-limit.reset-password.refill-seconds=10"
})
class ResetPasswordRateLimitIT {

  private static final int RATE_LIMIT_CAPACITY = 3;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should rate limit reset-password requests from the same IP after configured capacity is exceeded")
  void resetPassword_ipRateLimit_enforcesConfiguredCapacity() throws Exception {
    String ip = "10.10.10.100";
    String content = resetPasswordPayload();

    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performResetPassword(ip, content);

      assertThat(result.getResponse().getStatus())
        .as(
          "Reset-password attempt %d should reach reset-password processing before the IP rate limit is exceeded",
          attempt
        )
        .isNotEqualTo(429);
    }

    MvcResult rateLimitedResult = performResetPassword(ip, content);

    assertThat(rateLimitedResult.getResponse().getStatus())
      .as("The request exceeding the configured reset-password capacity should be rate limited")
      .isEqualTo(429);

    verifyRateLimitResponse(rateLimitedResult);
  }

  private MvcResult performResetPassword(String ip, String content) throws Exception {
    return mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .remoteAddress(ip))
      .andReturn();
  }

  private String resetPasswordPayload() throws Exception {
    return objectMapper.writeValueAsString(
      java.util.Map.of(
        "token", "invalid-reset-token-" + System.nanoTime(),
        "newPassword", "SecurePass123!@#"
      )
    );
  }

  private void verifyRateLimitResponse(MvcResult result) throws Exception {
    assertThat(result.getResponse().getHeader("Retry-After"))
      .as("Rate-limited reset-password responses must provide a Retry-After header")
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
}
