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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.login.capacity=3",
  "security.rate-limit.login.refill-seconds=10"
})
class LoginRateLimitIT {

  private static final int RATE_LIMIT_CAPACITY = 3;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should allow invalid login attempts up to IP capacity and then return 429")
  void login_ipRateLimit_enforcesConfiguredCapacity() throws Exception {
    String ip = "10.20.30.40";
    String email = uniqueEmail();
    String content = loginPayload(email);

    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performLogin(ip, content);

      assertThat(result.getResponse().getStatus())
        .as(
          "Invalid login attempt %d should reach authentication before IP rate limit is exceeded",
          attempt
        )
        .isEqualTo(401);
    }

    MvcResult rateLimitedResult = performLogin(ip, content);

    assertThat(rateLimitedResult.getResponse().getStatus())
      .as("The fourth request from the same IP should be rate limited")
      .isEqualTo(429);

    verifyRateLimitResponse(rateLimitedResult);
  }

  @Test
  @DisplayName("Should allow invalid login attempts up to email capacity and then return 429")
  void login_emailRateLimit_enforcesConfiguredCapacity() throws Exception {
    String ip = "10.20.30.41";
    String email = uniqueEmail();
    String content = loginPayload(email);

    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performLogin(ip, content);

      assertThat(result.getResponse().getStatus())
        .as(
          "Invalid login attempt %d should reach authentication before email rate limit is exceeded",
          attempt
        )
        .isEqualTo(401);
    }

    MvcResult rateLimitedResult = performLogin(ip, content);

    assertThat(rateLimitedResult.getResponse().getStatus())
      .as("The fourth request for the same email should be rate limited")
      .isEqualTo(429);

    verifyRateLimitResponse(rateLimitedResult);
  }

  @Test
  @DisplayName("Should keep IP rate-limit buckets independent")
  void login_differentIps_haveIndependentRateLimits() throws Exception {
    String limitedIp = "10.20.30.42";
    String independentIp = "10.20.30.43";

    exhaustIpLimitUsingDifferentEmails(limitedIp);

    MvcResult limitedResult = performLogin(
      limitedIp,
      loginPayload(uniqueEmail())
    );

    assertThat(limitedResult.getResponse().getStatus())
      .as("The exhausted IP should be rate limited")
      .isEqualTo(429);

    MvcResult independentResult = performLogin(
      independentIp,
      loginPayload(uniqueEmail())
    );

    assertThat(independentResult.getResponse().getStatus())
      .as("A different IP should have an independent IP rate-limit bucket")
      .isEqualTo(401);
  }

  @Test
  @DisplayName("Should keep email rate-limit buckets independent")
  void login_differentEmails_haveIndependentRateLimits() throws Exception {
    String limitedEmail = uniqueEmail();
    String independentEmail = uniqueEmail();

    exhaustEmailLimitUsingDifferentIps(limitedEmail);

    MvcResult limitedResult = performLogin(
      "10.20.30.44",
      loginPayload(limitedEmail)
    );

    assertThat(limitedResult.getResponse().getStatus())
      .as("The exhausted email should be rate limited")
      .isEqualTo(429);

    MvcResult independentResult = performLogin(
      "10.20.30.45",
      loginPayload(independentEmail)
    );

    assertThat(independentResult.getResponse().getStatus())
      .as("A different email should have an independent email rate-limit bucket")
      .isEqualTo(401);
  }

  @Test
  @DisplayName("Should return Retry-After and RATE_LIMITED error when login rate limit is exceeded")
  void login_rateLimitedResponse_containsRetryAfterAndError() throws Exception {
    String ip = "10.20.30.46";
    String email = uniqueEmail();
    String content = loginPayload(email);

    exhaustIpLimit(ip, content);

    MvcResult result = performLogin(ip, content);

    assertThat(result.getResponse().getStatus())
      .isEqualTo(429);

    verifyRateLimitResponse(result);

    String retryAfter = result.getResponse().getHeader("Retry-After");

    assertThat(retryAfter)
      .as("Rate-limited login responses must provide a Retry-After header")
      .isNotBlank();

    assertThat(Long.parseLong(retryAfter))
      .as("Retry-After must contain a positive number of seconds")
      .isPositive();
  }

  private void exhaustIpLimit(String ip, String content) throws Exception {
    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performLogin(ip, content);

      assertThat(result.getResponse().getStatus())
        .as(
          "IP request %d should reach authentication before the IP limit is exceeded",
          attempt
        )
        .isEqualTo(401);
    }
  }

  private void exhaustIpLimitUsingDifferentEmails(String ip) throws Exception {
    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      MvcResult result = performLogin(
        ip,
        loginPayload(uniqueEmail())
      );

      assertThat(result.getResponse().getStatus())
        .as(
          "Request %d should consume the shared IP bucket while using a fresh email bucket",
          attempt
        )
        .isEqualTo(401);
    }
  }

  private void exhaustEmailLimitUsingDifferentIps(String email) throws Exception {
    for (int attempt = 1; attempt <= RATE_LIMIT_CAPACITY; attempt++) {
      String ip = "10.20.31." + (40 + attempt);

      MvcResult result = performLogin(
        ip,
        loginPayload(email)
      );

      assertThat(result.getResponse().getStatus())
        .as(
          "Request %d should consume the shared email bucket while using a fresh IP bucket",
          attempt
        )
        .isEqualTo(401);
    }
  }

  private MvcResult performLogin(String ip, String content) throws Exception {
    return mockMvc.perform(
        post("/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content(content)
          .remoteAddress(ip)
      )
      .andReturn();
  }

  private void verifyRateLimitResponse(MvcResult result) throws Exception {
    assertThat(result.getResponse().getHeader("Retry-After"))
      .as("Rate-limited responses must provide a Retry-After header")
      .isNotBlank();

    ApiErrorResponse error = objectMapper.readValue(
      result.getResponse().getContentAsString(),
      ApiErrorResponse.class
    );

    assertThat(error.code())
      .isEqualTo("RATE_LIMITED");

    assertThat(error.message())
      .isEqualTo("Too many requests. Please try again later.");

    assertThat(error.timestamp())
      .isNotNull();
  }

  private String loginPayload(String email) throws Exception {
    return objectMapper.writeValueAsString(
      Map.of(
        "email", email,
        "password", "DefinitelyWrongPassword123!"
      )
    );
  }

  private String uniqueEmail() {
    return "login-ratelimit-" + UUID.randomUUID() + "@test.com";
  }
}
