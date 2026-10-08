package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.RefreshCookies;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// D-20: login and refresh return the app shell's display data in the body, so the frontend
// paints names without waiting for GET /me; the access token itself stays sub/rid/role/sid.
@IntegrationTest
@AutoConfigureMockMvc
class AuthSessionBodyIT {

  private static final String PASSWORD = "Password123!";

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private MembershipRepository membershipRepository;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void login_asManager_returnsExpiryRoleAndTheShellUser() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.member(email, PASSWORD, MembershipRole.MANAGER);
    String restaurantId = membershipRepository.findByUserId(user.getId()).orElseThrow().getRestaurantId();

    JsonNode body = body(login(email));

    assertThat(body.get("role").asText()).isEqualTo("MANAGER");
    String accessToken = body.get("accessToken").asText();
    Instant expiresAt = Instant.parse(body.get("expiresAt").asText());
    assertThat(expiresAt)
      .as("expiresAt is the access token's own exp")
      .isEqualTo(jwtService.extractExpiresAt(accessToken))
      .isAfter(Instant.now());

    JsonNode shell = body.get("user");
    assertThat(shell.get("id").asText()).isEqualTo(user.getId());
    assertThat(shell.get("firstName").asText()).isEqualTo("Test");
    assertThat(shell.get("lastName").asText()).isEqualTo("User");
    assertThat(shell.get("email").asText()).isEqualTo(email);
    assertThat(shell.get("avatarUrl").isNull()).isTrue();
    assertThat(shell.get("restaurantId").asText()).isEqualTo(restaurantId);
    assertThat(shell.get("restaurantName").asText()).isEqualTo("Test Restaurant");
    assertThat(shell.has("password")).isFalse();
    assertThat(body.has("refreshToken")).as("never in a body (D-10)").isFalse();
  }

  @Test
  void login_theAccessTokenStillCarriesNoDisplayData() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);

    String accessToken = body(login(email)).get("accessToken").asText();

    Set<String> claims = jwtService.extractClaim(accessToken, (Claims c) -> new HashSet<>(c.keySet()));
    assertThat(claims).containsExactlyInAnyOrder("sub", "rid", "role", "sid", "iat", "exp");
  }

  @Test
  void login_asPlatformAdmin_hasNoRestaurantInTheShellUser() throws Exception {
    String email = uniqueEmail();
    testUsers.platformAdmin(email, PASSWORD);

    JsonNode body = body(login(email));

    assertThat(body.get("role").asText()).isEqualTo("PLATFORM_ADMIN");
    assertThat(body.get("user").get("restaurantId").isNull()).isTrue();
    assertThat(body.get("user").get("restaurantName").isNull()).isTrue();
  }

  @Test
  void refresh_returnsTheSameShape_withNamesAsTheyAreNow() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);
    MvcResult login = login(email);
    String accessToken = body(login).get("accessToken").asText();

    mockMvc.perform(patch("/me")
        .header("Authorization", "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"firstName\":\"Renamed\"}"))
      .andExpect(status().isOk());

    JsonNode refreshed = body(mockMvc.perform(RefreshCookies.refresh(RefreshCookies.valueOf(login)))
      .andExpect(status().isOk())
      .andReturn());

    assertThat(refreshed.get("role").asText()).isEqualTo("OWNER");
    assertThat(refreshed.get("expiresAt").asText()).isNotBlank();
    assertThat(refreshed.get("user").get("firstName").asText())
      .as("read from the database on every refresh, so an edit shows on the next page load")
      .isEqualTo("Renamed");
  }

  private MvcResult login(String email) throws Exception {
    return mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static String uniqueEmail() {
    return "session-" + UUID.randomUUID() + "@test.com";
  }
}
