package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.RefreshCookies;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rotation, reuse detection and logout through the cookie, the only transport (D-10).
@IntegrationTest
@AutoConfigureMockMvc
class RefreshTokenFlowIT {

  private static final String RAW_PASSWORD = "Password123!";
  private static final String TEST_IP = "10.10.10.10";

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private String testEmail;

  @BeforeEach
  void setUp() {
    testEmail = "testuser_" + UUID.randomUUID() + "@example.com";

    testUsers.owner(testEmail, RAW_PASSWORD);
  }

  @Test
  void refresh_rotatesOnEveryCall_andLogoutRevokes() throws Exception {
    String first = login();

    String second = refreshToken(refresh(first).andExpect(status().isOk()));
    assertThat(second).isNotBlank().isNotEqualTo(first);

    String third = refreshToken(refresh(second).andExpect(status().isOk()));
    assertThat(third).isNotBlank().isNotEqualTo(second).isNotEqualTo(first);

    logout(third).andExpect(status().isOk());

    refresh(third)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    logout(third).andExpect(status().isOk());
  }

  @Test
  void refresh_reuseOfARotatedToken_revokesTheWholeFamily() throws Exception {
    String first = login();
    String second = refreshToken(refresh(first).andExpect(status().isOk()));

    refresh(first)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // The guard against a rolled-back revocation: the successor must be dead too.
    refresh(second)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void refresh_reuseInOneFamily_leavesAnotherLoginAlive() throws Exception {
    String deviceA = login();
    String deviceB = login();

    refresh(deviceA).andExpect(status().isOk());
    refresh(deviceA).andExpect(status().isUnauthorized());

    refresh(deviceB).andExpect(status().isOk());
  }

  @Test
  void logout_presentingAnAlreadyRotatedToken_revokesTheWholeFamily() throws Exception {
    String first = login();
    String second = refreshToken(refresh(first).andExpect(status().isOk()));

    // "first" was already rotated away (used) by the refresh above; logout still resolves it
    // by hash and must revoke the whole family, killing "second" too, not just "first".
    logout(first).andExpect(status().isOk());

    refresh(second)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void refresh_unknownToken_returns401() throws Exception {
    refresh("not-a-real-token")
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  // Acceptance 5: after a member is deactivated, their next refresh gets 401.
  @Test
  void refresh_afterDeactivation_returns401_andTheFamilyStaysDead() throws Exception {
    String token = login();
    User user = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
    user.setActive(false);
    userRepository.save(user);

    refresh(token)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    user.setActive(true);
    userRepository.save(user);
    // Reactivation does not revive the revoked session; the member signs in again.
    refresh(token).andExpect(status().isUnauthorized());
  }

  private String login() throws Exception {
    return RefreshCookies.valueOf(mockMvc.perform(post("/auth/login")
        .with(request -> {
          request.setRemoteAddr(TEST_IP);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(testEmail, RAW_PASSWORD))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andReturn());
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(RefreshCookies.refresh(refreshToken)
      .with(request -> {
        request.setRemoteAddr(TEST_IP);
        return request;
      }));
  }

  private ResultActions logout(String refreshToken) throws Exception {
    return mockMvc.perform(RefreshCookies.logout(refreshToken)
      .with(request -> {
        request.setRemoteAddr(TEST_IP);
        return request;
      }));
  }

  private String refreshToken(ResultActions result) {
    return RefreshCookies.valueOf(result.andReturn());
  }
}
