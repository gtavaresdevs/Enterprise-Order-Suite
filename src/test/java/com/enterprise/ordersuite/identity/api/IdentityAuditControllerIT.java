package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class IdentityAuditControllerIT {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  UserRepository userRepository;

  @Autowired
  RoleRepository roleRepository;

  @Autowired
  IdentityAuditEventRepository auditRepository;

  @Autowired
  PasswordEncoder passwordEncoder;

  private Role adminRole;
  private Role superAdminRole;

  @BeforeEach
  void setup() {
    adminRole = roleRepository.findByName("ADMIN").orElseThrow();
    superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
  }

  @Test
  void listAudit_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/admin/identity-audit"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void listAudit_nonSuperAdmin_returns403() throws Exception {
    String token = createUserAndLogin(
      adminRole,
      "admin-" + UUID.randomUUID() + "@test.com"
    );

    mockMvc.perform(get("/admin/identity-audit?page=0&size=10")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void listAudit_superAdmin_returns200() throws Exception {
    String token = createUserAndLogin(
      superAdminRole,
      "super-" + UUID.randomUUID() + "@test.com"
    );

    mockMvc.perform(get("/admin/identity-audit?page=0&size=10")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray());
  }

  private String createUserAndLogin(Role role, String email) throws Exception {
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode("Password123!"));
    user.setRole(role);
    user.setActive(true);
    user.setFirstName("Test");
    user.setLastName("User");

    userRepository.save(user);

    AuthRequest payload = new AuthRequest(email, "Password123!");

    var result = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(payload)))
      .andExpect(status().isOk())
      .andReturn();

    return objectMapper
      .readTree(result.getResponse().getContentAsString())
      .get("accessToken")
      .asText();
  }
}
