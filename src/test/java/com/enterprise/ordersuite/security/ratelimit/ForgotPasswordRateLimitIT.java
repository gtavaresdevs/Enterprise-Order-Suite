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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.forgot-password.capacity=3",
  "security.rate-limit.forgot-password.refill-seconds=10"
})
class ForgotPasswordRateLimitIT {

  private static final int RATE_LIMIT_CAPACITY = 3;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should allow requests up to configured capacity and return 429 when capacity is exceeded")
  void forgotPassword_enforcesConfiguredCapacity() throws Exception {
    String ip = uniqueIp();
    String email = uniqueEmail();
    String content = forgotPasswordPayload(email);

    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      int finalAttempt = attempt;
      mockMvc.perform(post("/auth/forgot-password")
          .contentType(MediaType.APPLICATION_JSON)
          .content(content)
          .remoteAddress(ip))
        .andExpect(result -> {
          int actualStatus = result.getResponse().getStatus();

          assertThat(actualStatus)
            .as("Request %d of %d should not be rate limited", finalAttempt, RATE_LIMIT_CAPACITY)
            .isNotEqualTo(429);
        });
    }

    MvcResult rateLimitedResult = mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .remoteAddress(ip))
      .andExpect(status().isTooManyRequests())
      .andReturn();

    verifyRateLimitResponse(rateLimitedResult);
  }

  @Test
  @DisplayName("Should return Retry-After and RATE_LIMITED error when request is rate limited")
  void forgotPassword_rateLimitedResponse_containsRetryAfterAndError() throws Exception {
    String ip = uniqueIp();
    String email = uniqueEmail();
    String content = forgotPasswordPayload(email);

    exhaustLimit(ip, content);

    MvcResult result = mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .remoteAddress(ip))
      .andExpect(status().isTooManyRequests())
      .andReturn();

    verifyRateLimitResponse(result);

    String retryAfter = result.getResponse().getHeader("Retry-After");

    assertThat(retryAfter)
      .as("Rate-limited responses must provide a Retry-After header")
      .isNotBlank();

    assertThat(Long.parseLong(retryAfter))
      .as("Retry-After must contain a positive number of seconds")
      .isPositive();
  }

  @Test
  @DisplayName("Should keep rate-limit buckets independent for different IP addresses")
  void forgotPassword_independentPerIp() throws Exception {
    String email = uniqueEmail();

    String ip1 = uniqueIp();
    String ip2 = uniqueIp();

    String content = forgotPasswordPayload(email);

    exhaustLimit(ip1, content);

    mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .remoteAddress(ip2))
      .andExpect(result -> {
        assertThat(result.getResponse().getStatus())
          .as("A different IP must use a different rate-limit key")
          .isNotEqualTo(429);
      });
  }

  @Test
  @DisplayName("Should keep rate-limit buckets independent for different emails from the same IP")
  void forgotPassword_independentPerEmailSameIp() throws Exception {
    String ip = uniqueIp();

    String email1 = uniqueEmail();
    String email2 = uniqueEmail();

    String email1Content = forgotPasswordPayload(email1);
    String email2Content = forgotPasswordPayload(email2);

    exhaustLimit(ip, email1Content);

    mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(email1Content)
        .remoteAddress(ip))
      .andExpect(status().isTooManyRequests());

    mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(email2Content)
        .remoteAddress(ip))
      .andExpect(result -> {
        assertThat(result.getResponse().getStatus())
          .as("A different email must use a different composite rate-limit key")
          .isNotEqualTo(429);
      });
  }

  @Test
  @DisplayName("Should reject oversized auth payload before processing the request")
  void forgotPassword_rejectsOversizedPayload() throws Exception {
    String ip = uniqueIp();

    String oversizedPadding = "a".repeat(9000);
    String oversizedContent = """
      {"email":"test@test.com","padding":"%s"}
      """.formatted(oversizedPadding);

    MvcResult result = mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(oversizedContent)
        .remoteAddress(ip))
      .andExpect(status().isBadRequest())
      .andReturn();

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );

    assertThat(error.code()).isEqualTo("BAD_REQUEST");
    assertThat(error.message())
      .isEqualTo("Payload size exceeds maximum allowed limit.");
  }

  private void exhaustLimit(String ip, String content) throws Exception {
    for (int attempt = 0; attempt < RATE_LIMIT_CAPACITY; attempt++) {
      int finalAttempt = attempt;
      mockMvc.perform(post("/auth/forgot-password")
          .contentType(MediaType.APPLICATION_JSON)
          .content(content)
          .remoteAddress(ip))
        .andExpect(result -> {
          assertThat(result.getResponse().getStatus())
            .as("Request %d should still be within the configured rate limit", finalAttempt + 1)
            .isNotEqualTo(429);
        });
    }
  }

  private void verifyRateLimitResponse(MvcResult result) throws Exception {
    assertThat(result.getResponse().getHeader("Retry-After"))
      .as("Rate-limited responses must provide a Retry-After header")
      .isNotBlank();

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );

    assertThat(error.code()).isEqualTo("RATE_LIMITED");
    assertThat(error.message()).isEqualTo("Too many requests. Please try again later.");
    assertThat(error.timestamp()).isNotNull();
  }

  private String forgotPasswordPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
      java.util.Map.of("email", email)
    );
  }

  private String uniqueEmail() {
    return "ratelimit-" + UUID.randomUUID() + "@test.com";
  }

  private String uniqueIp() {
    UUID uuid = UUID.randomUUID();

    int octet2 = Math.abs(uuid.hashCode() % 254) + 1;
    int octet3 = Math.abs(uuid.hashCode() / 254 % 254) + 1;
    int octet4 = Math.abs(uuid.hashCode() / 64516 % 254) + 1;

    return "10." + octet2 + "." + octet3 + "." + octet4;
  }
}
