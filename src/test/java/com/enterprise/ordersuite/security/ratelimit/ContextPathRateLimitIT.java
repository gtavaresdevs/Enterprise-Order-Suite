package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Every other rate-limit IT sends requests without a context path, which is how the limiter
// shipped switched off: production serves under SERVER_CONTEXT_PATH=/api, and the filter
// compared the raw request URI (/api/auth/login) with "/auth/login".
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.login.capacity=5",
  "security.rate-limit.login.refill-seconds=60"
})
class ContextPathRateLimitIT {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("Should rate limit login when the application is served under the /api context path")
  void login_underTheApiContextPath_isRateLimited() throws Exception {
    List<Integer> statuses = sixFailedLogins("/api", "10.20.30.1");

    assertThat(statuses.subList(0, 5))
      .as("the first five attempts are within capacity")
      .doesNotContain(429);
    assertThat(statuses.get(5))
      .as("the sixth attempt exceeds capacity - before the fix every attempt here was 401")
      .isEqualTo(429);
  }

  @Test
  @DisplayName("Should still rate limit login when there is no context path")
  void login_withoutAContextPath_isStillRateLimited() throws Exception {
    assertThat(sixFailedLogins("", "10.20.30.2").get(5)).isEqualTo(429);
  }

  @Test
  @DisplayName("Should rate limit login when an unreserved character of the path is percent-encoded")
  void login_withAPercentEncodedPath_isRateLimited() throws Exception {
    List<Integer> statuses = sixFailedLogins("/api", "/auth/%6cogin", "10.20.30.3");

    assertThat(statuses.get(0))
      .as("Spring MVC routes on the decoded path, so the encoded path reaches the login endpoint")
      .isEqualTo(401);
    assertThat(statuses.get(5))
      .as("the limiter must match the decoded path too - before the fix every attempt here was 401")
      .isEqualTo(429);
  }

  private List<Integer> sixFailedLogins(String contextPath, String ip) throws Exception {
    return sixFailedLogins(contextPath, "/auth/login", ip);
  }

  private List<Integer> sixFailedLogins(String contextPath, String path, String ip) throws Exception {
    String body = "{\"email\":\"ctx-" + UUID.randomUUID() + "@test.com\",\"password\":\"wrong\"}";
    List<Integer> statuses = new ArrayList<>();
    for (int attempt = 0; attempt < 6; attempt++) {
      // URI.create keeps the escape: a String template would be re-encoded or decoded by the builder.
      statuses.add(mockMvc.perform(post(URI.create(contextPath + path))
          .contextPath(contextPath)
          .with(request -> {
            request.setRemoteAddr(ip);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(body))
        .andReturn().getResponse().getStatus());
    }
    return statuses;
  }
}
