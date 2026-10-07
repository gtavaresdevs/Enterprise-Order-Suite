package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.domain.PasswordResetToken;
import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.ForgotPasswordRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.ResetPasswordRequest;
import com.enterprise.ordersuite.auth.persistence.PasswordResetTokenRepository;
import com.enterprise.ordersuite.auth.service.PasswordResetService;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestEmailServiceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Import(TestEmailServiceConfig.class)
class AuthenticationControllerIT {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordResetTokenRepository tokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private PasswordResetService passwordResetService;

  @Autowired
  private TestEmailServiceConfig.CapturingEmailService capturingEmailService;

  @BeforeEach
  void setup() {
    capturingEmailService.clear();
    tokenRepository.deleteAll();
  }

  // Signup is invite-only (Tenancy & Identity D-5): the public registration endpoint is gone.
  @Test
  void register_isGone() throws Exception {
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@test.com\",\"password\":\"SecurePass123!@#\"}"))
      .andExpect(status().isNotFound());

    assertThat(userRepository.findByEmailIgnoreCase("a@test.com")).isEmpty();
  }

  @Test
  void forgotPassword_ValidEmail_Returns200_AndSendsEmail() throws Exception {

    String email = "integration-" + UUID.randomUUID() + "@test.com";

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode("OldPass123!"));
    user.setActive(true);
    user.setFirstName("Integration");
    user.setLastName("User");
    userRepository.save(user);

    ForgotPasswordRequest request = new ForgotPasswordRequest();
    request.setEmail(email);

    mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    assertThat(capturingEmailService.sent()).hasSize(1);
    assertThat(capturingEmailService.sent().get(0).toEmail()).isEqualTo(email);
    assertThat(capturingEmailService.sent().get(0).resetUrl()).contains("token=");

    assertThat(
      tokenRepository.findAll().stream()
        .anyMatch(token -> token.getUser().getId().equals(user.getId()))
    ).isTrue();
  }

  @Test
  void forgotPassword_InvalidEmail_Returns200_GracefulFail_NoEmail() throws Exception {

    ForgotPasswordRequest request = new ForgotPasswordRequest();
    request.setEmail("ghost-" + UUID.randomUUID() + "@test.com");

    mockMvc.perform(post("/auth/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    assertThat(capturingEmailService.sent()).isEmpty();
  }

  @Test
  void resetPassword_ValidToken_Returns200_AndChangesPassword() throws Exception {

    String email = "reset-" + UUID.randomUUID() + "@test.com";

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode("OldPass123!"));
    user.setActive(true);
    user.setFirstName("Reset");
    user.setLastName("User");
    userRepository.save(user);

    String rawToken =
      passwordResetService.requestPasswordReset(email).orElseThrow();

    ResetPasswordRequest request = new ResetPasswordRequest();
    request.setToken(rawToken);
    request.setNewPassword("NewEnterprisePass!@#");

    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    User updatedUser =
      userRepository.findByEmailIgnoreCase(email).orElseThrow();

    assertThat(
      passwordEncoder.matches(
        "NewEnterprisePass!@#",
        updatedUser.getPassword()
      )
    ).isTrue();

    PasswordResetToken token =
      tokenRepository.findAll()
        .stream()
        .filter(t -> t.getUser().getId().equals(user.getId()))
        .findFirst()
        .orElseThrow();

    assertThat(token.isUsed()).isTrue();
  }

  @Test
  void resetPassword_InvalidToken_Returns4xxClientError() throws Exception {

    ResetPasswordRequest request = new ResetPasswordRequest();
    request.setToken("invalid-hacker-token-string");
    request.setNewPassword("NewPass123!@#");

    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().is4xxClientError());
  }

  @Test
  void resetPassword_revokesEveryRefreshTokenOfThatUser_andNoOneElses() throws Exception {
    String victim = saveActiveUser("OldPass123!");
    String bystander = saveActiveUser("OldPass123!");
    String victimPhone = loginForRefreshToken(victim, "OldPass123!");
    String victimLaptop = loginForRefreshToken(victim, "OldPass123!");
    String bystanderToken = loginForRefreshToken(bystander, "OldPass123!");

    String rawToken = passwordResetService.requestPasswordReset(victim).orElseThrow();
    ResetPasswordRequest request = new ResetPasswordRequest();
    request.setToken(rawToken);
    request.setNewPassword("NewEnterprisePass!@#");
    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    assertThat(refreshStatus(victimPhone)).as("a reset must end every existing session").isEqualTo(401);
    assertThat(refreshStatus(victimLaptop)).as("a reset must end every existing session").isEqualTo(401);
    assertThat(refreshStatus(bystanderToken)).as("another user's session is untouched").isEqualTo(200);
  }

  private String saveActiveUser(String rawPassword) {
    String email = "reset-" + UUID.randomUUID() + "@test.com";
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setActive(true);
    user.setFirstName("Reset");
    user.setLastName("User");
    userRepository.save(user);
    return email;
  }

  private String loginForRefreshToken(String email, String rawPassword) throws Exception {
    String body = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, rawPassword))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("refreshToken").asText();
  }

  private int refreshStatus(String refreshToken) throws Exception {
    return mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
      .andReturn().getResponse().getStatus();
  }
}
