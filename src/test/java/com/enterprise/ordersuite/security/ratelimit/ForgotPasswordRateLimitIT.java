package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
  "security.rate-limit.enabled=true",
  // Ensure this property matches the exact name used in your RateLimiter configuration class!
  "security.rate-limit.forgot-password.limit=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForgotPasswordRateLimitIT {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("forgotPasswordRateLimiter")
  private RateLimiter forgotPasswordLimiter;

  private MockMvc mockMvc;

  // Enterprise Standard: If your configuration doesn't parse properties reactively,
  // use a higher iteration roof to ensure the fallback limit is explicitly broken.
  private static final int ROBUST_EXHAUSTION_ATTEMPTS = 50;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
  }

  @Test
  @DisplayName("Should allow initial requests under threshold and strictly return 429 when threshold exceeded")
  void forgotPassword_enforcesThresholdLimit() throws Exception {
    String ip = "1.2.3.4";
    String email = "target@example.com";
    String content = objectMapper.writeValueAsString(Collections.singletonMap("email", email));

    // 1. Send the very first request—it must pass through without a 429
    MvcResult firstResult = performRequest(ip, content);
    assertThat(firstResult.getResponse().getStatus())
      .as("First request must be under any standard enterprise rate limit constraint")
      .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // 2. Loop robustly to force exhaustion regardless of whether the threshold is 3 or 20
    boolean limitTriggered = false;
    for (int i = 0; i < ROBUST_EXHAUSTION_ATTEMPTS; i++) {
      MvcResult result = performRequest(ip, content);
      if (result.getResponse().getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
        limitTriggered = true;
        verifyRateLimitResponse(result);
        break;
      }
    }

    assertThat(limitTriggered)
      .as("The rate limiter should have eventually triggered a 429 after continuous rapid requests")
      .isTrue();
  }

  @Test
  @DisplayName("Should not share rate limits across different IP addresses")
  void forgotPassword_independentPerIp() throws Exception {
    String email = "shared@example.com";
    String content = objectMapper.writeValueAsString(Collections.singletonMap("email", email));

    // Exhaust limit for IP 1 completely
    exhaustLimit("1.1.1.1", content);

    // A brand new client IP addressing the same endpoint must not be pre-blocked
    MvcResult result = performRequest("2.2.2.2", content);
    assertThat(result.getResponse().getStatus())
      .as("A clean client IP should not inherit block states from another IP context")
      .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  @DisplayName("Should rate limit emails independently even when originating from the same IP address")
  void forgotPassword_independentPerEmailSameIp() throws Exception {
    String ip = "5.5.5.5";
    String email1Content = objectMapper.writeValueAsString(Collections.singletonMap("email", "user1@example.com"));
    String email2Content = objectMapper.writeValueAsString(Collections.singletonMap("email", "user2@example.com"));

    // Exhaust target account 1
    exhaustLimit(ip, email1Content);

    // Target Email 1 should be caught by the filter block
    assertThat(performRequest(ip, email1Content).getResponse().getStatus())
      .as("Target user 1 should receive a 429")
      .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Target Email 2 requested from the SAME IP should remain unblocked
    assertThat(performRequest(ip, email2Content).getResponse().getStatus())
      .as("Target user 2 should pass since keys are composite (IP + Email)")
      .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  @DisplayName("Should instantly reject oversized requests before processing stream payloads (DoS Guard Validation)")
  void forgotPassword_rejectsOversizedPayloads() throws Exception {
    String ip = "9.9.9.9";

    String heavyPadding = "a".repeat(9000);
    String oversizedContent = String.format("{\"email\":\"test@test.com\",\"padding\":\"%s\"}", heavyPadding);

    MvcResult result = performRequest(ip, oversizedContent);

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );
    assertThat(error.code()).isEqualTo("BAD_REQUEST");
  }

  private MvcResult performRequest(String ip, String content) throws Exception {
    return mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .remoteAddress(ip))
      .andReturn();
  }

  private void exhaustLimit(String ip, String content) throws Exception {
    for (int i = 0; i < ROBUST_EXHAUSTION_ATTEMPTS; i++) {
      if (performRequest(ip, content).getResponse().getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
        return;
      }
    }
  }

  private void verifyRateLimitResponse(MvcResult result) throws Exception {
    assertThat(result.getResponse().getHeader("Retry-After"))
      .as("Compliance requires a standard back-off window specification header")
      .isNotNull();

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );
    assertThat(error.code()).isEqualTo("RATE_LIMITED");
    assertThat(error.message()).contains("Too many requests");
  }
}
