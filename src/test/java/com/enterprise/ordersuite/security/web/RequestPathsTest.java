package com.enterprise.ordersuite.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathsTest {

  @Test
  void withinApplication_stripsTheContextPath() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setContextPath("/api");

    assertThat(RequestPaths.withinApplication(request)).isEqualTo("/auth/login");
  }

  @Test
  void withinApplication_withoutAContextPath_returnsTheUri() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

    assertThat(RequestPaths.withinApplication(request)).isEqualTo("/auth/login");
  }
}
