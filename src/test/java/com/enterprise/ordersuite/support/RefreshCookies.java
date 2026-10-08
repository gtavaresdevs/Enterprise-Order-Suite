package com.enterprise.ordersuite.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// The refresh token travels only in the HttpOnly cookie, and /auth/refresh and /auth/logout
// check Origin on every call (Tenancy & Identity D-10). FRONTEND is the allowed origin that
// application-test.yml pins.
public final class RefreshCookies {

  public static final String NAME = "refreshToken";
  public static final String FRONTEND = "http://localhost:3000";

  private RefreshCookies() {
  }

  // The refresh token a login or refresh response set.
  public static String valueOf(MvcResult result) {
    String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(header).as("response must set the refresh cookie").startsWith(NAME + "=");
    return header.substring((NAME + "=").length(), header.indexOf(';'));
  }

  public static MockHttpServletRequestBuilder refresh(String token) {
    return post("/auth/refresh").cookie(new Cookie(NAME, token)).header(HttpHeaders.ORIGIN, FRONTEND);
  }

  public static MockHttpServletRequestBuilder logout(String token) {
    return post("/auth/logout").cookie(new Cookie(NAME, token)).header(HttpHeaders.ORIGIN, FRONTEND);
  }
}
