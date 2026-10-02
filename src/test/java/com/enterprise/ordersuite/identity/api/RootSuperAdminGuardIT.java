package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.api.dto.AdminUpdateUserRequest;
import com.enterprise.ordersuite.identity.api.dto.SetUserRoleRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The anti-lockout guard on the root super admin, now that its address is configuration
 * rather than a constant compiled into UserAdminService.
 *
 * <p>Binding is the thing under test as much as the rule is: a property that silently fails
 * to bind leaves an empty address, and an empty address protects nobody. Every assertion here
 * would still pass against a guard that had been switched off, except that the last one
 * proves a non-root account is *not* protected — together they pin the guard to exactly one
 * account.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "app.root-super-admin.email=root-guard@test.com"
})
class RootSuperAdminGuardIT {

  private static final String ROOT_EMAIL = "root-guard@test.com";
  private static final String DEFAULT_PASSWORD = "Password123!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private User rootUser;
  private String superAdminToken;

  @BeforeEach
  void setUp() throws Exception {
    // The root address is fixed by the property above, so it cannot be randomised per test
    // the way the house style otherwise requires. Reuse the row if an earlier test in this
    // class already created it - the container is shared across the class.
    rootUser = userRepository.findByEmailIgnoreCase(ROOT_EMAIL)
      .orElseGet(() -> createUser("SUPER_ADMIN", ROOT_EMAIL));

    String actingEmail = "acting-" + UUID.randomUUID() + "@test.com";
    createUser("SUPER_ADMIN", actingEmail);

    superAdminToken = loginAndGetAccessToken(actingEmail);
  }

  @Test
  void deactivate_theRootSuperAdmin_isRefused() throws Exception {
    mockMvc.perform(post("/admin/users/" + rootUser.getId() + "/deactivate")
        .header("Authorization", "Bearer " + superAdminToken))
      .andExpect(status().isBadRequest());

    assertThat(userRepository.findById(rootUser.getId()).orElseThrow().getActive())
      .as("the root account must still be active after a refused deactivation")
      .isTrue();
  }

  @Test
  void setRole_onTheRootSuperAdmin_isRefused() throws Exception {
    mockMvc.perform(patch("/admin/users/" + rootUser.getId() + "/role")
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new SetUserRoleRequest("USER"))))
      .andExpect(status().isBadRequest());

    assertThat(userRepository.findById(rootUser.getId()).orElseThrow().getRole().getName())
      .as("the root account must keep SUPER_ADMIN after a refused role change")
      .isEqualTo("SUPER_ADMIN");
  }

  @Test
  void updateEmail_onTheRootSuperAdmin_isRefused() throws Exception {
    AdminUpdateUserRequest request =
      new AdminUpdateUserRequest(null, null, "hijacked-" + UUID.randomUUID() + "@test.com");

    mockMvc.perform(patch("/admin/users/" + rootUser.getId())
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isBadRequest());

    assertThat(userRepository.findById(rootUser.getId()).orElseThrow().getEmail())
      .as("the root account must keep its configured address after a refused email change")
      .isEqualToIgnoringCase(ROOT_EMAIL);
  }

  @Test
  void deactivate_anAccountThatIsNotTheRoot_isAllowed() throws Exception {
    User ordinary = createUser("USER", "ordinary-" + UUID.randomUUID() + "@test.com");

    mockMvc.perform(post("/admin/users/" + ordinary.getId() + "/deactivate")
        .header("Authorization", "Bearer " + superAdminToken))
      .andExpect(status().isOk());

    assertThat(userRepository.findById(ordinary.getId()).orElseThrow().getActive())
      .as("the guard must protect the configured address only, not every account")
      .isFalse();
  }

  private User createUser(String roleName, String email) {
    Role role = roleRepository.findByName(roleName).orElseThrow();

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
    user.setRole(role);
    user.setActive(true);
    user.setFirstName("Root");
    user.setLastName("Guard");

    return userRepository.save(user);
  }

  private String loginAndGetAccessToken(String email) throws Exception {
    AuthRequest request = new AuthRequest(email, DEFAULT_PASSWORD);

    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("accessToken")
      .asText();
  }
}
