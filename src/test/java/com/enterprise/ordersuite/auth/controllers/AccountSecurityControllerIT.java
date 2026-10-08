package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.ChangePasswordRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.RefreshCookies;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Own account 2 and 3, acceptance 6: after POST /me/password or POST /me/sign-out-other-devices
// another browser's refresh gets 401 and the current session continues. The current session
// is the access token's sid claim, because the refresh cookie never reaches /me/*.
@IntegrationTest
@AutoConfigureMockMvc
class AccountSecurityControllerIT {

  private static final String PASSWORD = "Password123!";
  private static final String NEW_PASSWORD = "NewPassword456!";

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private MembershipRepository membershipRepository;

  @Autowired
  private IdentityAuditEventRepository auditRepository;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private record Session(String accessToken, String refreshToken) {}

  // -------- POST /me/password --------

  @Test
  void changePassword_withoutToken_returns401() throws Exception {
    mockMvc.perform(post("/me/password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD))))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void changePassword_signsOutOtherSessions_keepsTheCurrentOne_andSwapsTheCredential() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    Session current = login(email, PASSWORD);
    Session otherBrowser = login(email, PASSWORD);

    changePassword(current.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    mockMvc.perform(RefreshCookies.refresh(otherBrowser.refreshToken()))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    // The current session continues.
    mockMvc.perform(RefreshCookies.refresh(current.refreshToken())).andExpect(status().isOk());

    loginStatus(email, PASSWORD).andExpect(status().isUnauthorized());
    loginStatus(email, NEW_PASSWORD).andExpect(status().isOk());

    String restaurantId = membershipRepository.findByUserId(user.getId()).orElseThrow().getRestaurantId();
    assertThat(eventsFor(user, IdentityAuditEventType.PASSWORD_CHANGED))
      .singleElement()
      .satisfies(event -> {
        assertThat(event.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getDetails()).as("no secrets in the audit log").isEmpty();
      });
  }

  @Test
  void changePassword_afterTheCurrentSessionRotated_stillKeepsIt() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);
    Session first = login(email, PASSWORD);
    Session rotated = refresh(first.refreshToken());

    changePassword(rotated.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    // sid names the family, which survives rotation.
    mockMvc.perform(RefreshCookies.refresh(rotated.refreshToken())).andExpect(status().isOk());
  }

  @Test
  void changePassword_wrongCurrentPassword_returns400_andChangesNothing() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    Session current = login(email, PASSWORD);
    Session otherBrowser = login(email, PASSWORD);

    changePassword(current.accessToken(), "WrongPassword1!", NEW_PASSWORD)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

    loginStatus(email, PASSWORD).andExpect(status().isOk());
    mockMvc.perform(RefreshCookies.refresh(otherBrowser.refreshToken())).andExpect(status().isOk());
    assertThat(eventsFor(user, IdentityAuditEventType.PASSWORD_CHANGED)).isEmpty();
  }

  @Test
  void changePassword_toTheCurrentPassword_returns409() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);

    changePassword(login(email, PASSWORD).accessToken(), PASSWORD, PASSWORD)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PASSWORD_REUSE_ERROR"));
  }

  @Test
  void changePassword_backToAPreviousPassword_returns409() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);
    Session session = login(email, PASSWORD);
    changePassword(session.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    changePassword(session.accessToken(), NEW_PASSWORD, PASSWORD)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PASSWORD_REUSE_ERROR"));
  }

  @Test
  void changePassword_tooShort_returns400() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);

    changePassword(login(email, PASSWORD).accessToken(), PASSWORD, "short")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void changePassword_asPlatformAdmin_works_andAuditsWithoutARestaurant() throws Exception {
    String email = uniqueEmail();
    User admin = testUsers.platformAdmin(email, PASSWORD);

    changePassword(login(email, PASSWORD).accessToken(), PASSWORD, NEW_PASSWORD)
      .andExpect(status().isNoContent());

    assertThat(eventsFor(admin, IdentityAuditEventType.PASSWORD_CHANGED))
      .singleElement()
      .satisfies(event -> assertThat(event.getRestaurantId()).isNull());
  }

  // -------- POST /me/sign-out-other-devices --------

  @Test
  void signOutOtherDevices_withoutToken_returns401() throws Exception {
    mockMvc.perform(post("/me/sign-out-other-devices")).andExpect(status().isUnauthorized());
  }

  @Test
  void signOutOtherDevices_revokesEveryOtherSession_andKeepsTheCurrentOne() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    Session current = login(email, PASSWORD);
    Session phone = login(email, PASSWORD);
    Session laptop = login(email, PASSWORD);

    signOutOthers(current.accessToken()).andExpect(status().isNoContent());

    mockMvc.perform(RefreshCookies.refresh(phone.refreshToken())).andExpect(status().isUnauthorized());
    mockMvc.perform(RefreshCookies.refresh(laptop.refreshToken())).andExpect(status().isUnauthorized());
    mockMvc.perform(RefreshCookies.refresh(current.refreshToken())).andExpect(status().isOk());
    assertThat(eventsFor(user, IdentityAuditEventType.SIGNED_OUT_OTHER_DEVICES)).hasSize(1);
  }

  @Test
  void signOutOtherDevices_leavesOtherUsersSessionsAlone() throws Exception {
    String email = uniqueEmail();
    String otherEmail = uniqueEmail();
    testUsers.owner(email, PASSWORD);
    testUsers.owner(otherEmail, PASSWORD);
    Session mine = login(email, PASSWORD);
    Session someoneElse = login(otherEmail, PASSWORD);

    signOutOthers(mine.accessToken()).andExpect(status().isNoContent());

    mockMvc.perform(RefreshCookies.refresh(someoneElse.refreshToken())).andExpect(status().isOk());
  }

  @Test
  void signOutOtherDevices_withATokenThatNamesNoSession_signsOutEverySession() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    Session session = login(email, PASSWORD);
    String restaurantId = membershipRepository.findByUserId(user.getId()).orElseThrow().getRestaurantId();
    String withoutSid = jwtService.generateToken(user.getId(), restaurantId, "OWNER", null);

    signOutOthers(withoutSid).andExpect(status().isNoContent());

    // Without sid no session can be spared: fail towards signing out.
    mockMvc.perform(RefreshCookies.refresh(session.refreshToken())).andExpect(status().isUnauthorized());
  }

  private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
    return mockMvc.perform(post("/me/password")
      .header("Authorization", "Bearer " + accessToken)
      .contentType(MediaType.APPLICATION_JSON)
      .content(json(new ChangePasswordRequest(current, next))));
  }

  private ResultActions signOutOthers(String accessToken) throws Exception {
    return mockMvc.perform(post("/me/sign-out-other-devices").header("Authorization", "Bearer " + accessToken));
  }

  private List<IdentityAuditEvent> eventsFor(User user, IdentityAuditEventType type) {
    return auditRepository.findAll().stream()
      .filter(e -> e.getType() == type && user.getId().equals(e.getTargetUserId()))
      .toList();
  }

  private Session login(String email, String password) throws Exception {
    return session(loginStatus(email, password).andExpect(status().isOk()).andReturn());
  }

  private ResultActions loginStatus(String email, String password) throws Exception {
    return mockMvc.perform(post("/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .content(json(new AuthRequest(email, password))));
  }

  private Session refresh(String refreshToken) throws Exception {
    return session(mockMvc.perform(RefreshCookies.refresh(refreshToken)).andExpect(status().isOk()).andReturn());
  }

  private Session session(MvcResult result) throws Exception {
    String accessToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    return new Session(accessToken, RefreshCookies.valueOf(result));
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private static String uniqueEmail() {
    return "account-" + UUID.randomUUID() + "@test.com";
  }
}
