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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class AdminUsersControllerIT {

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

  private Role userRole;
  private Role superAdminRole;

  @BeforeEach
  void setup() {
    userRole = roleRepository.findByName("USER").orElseThrow();
    superAdminRole = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
  }

  @Test
  void deactivate_requiresSuperAdmin_403ForNonSuperAdmin() throws Exception {
    Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

    String adminEmail = "admin-" + UUID.randomUUID() + "@test.com";

    User admin = new User();
    admin.setEmail(adminEmail);
    admin.setPassword(passwordEncoder.encode("Password123!"));
    admin.setRole(adminRole);
    admin.setActive(true);
    admin.setFirstName("Standard");
    admin.setLastName("Admin");

    userRepository.save(admin);

    String token = loginAndGetAccessToken(adminEmail, "Password123!");
    User target = createTargetUser();

    mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
        .header("Authorization", "Bearer " + token))
      .andDo(print())
      .andExpect(status().isForbidden());
  }

  @Test
  void reactivate_requiresSuperAdmin_403ForNonSuperAdmin() throws Exception {
    Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

    String adminEmail = "admin-" + UUID.randomUUID() + "@test.com";

    User admin = new User();
    admin.setEmail(adminEmail);
    admin.setPassword(passwordEncoder.encode("Password123!"));
    admin.setRole(adminRole);
    admin.setActive(true);
    admin.setFirstName("Standard");
    admin.setLastName("Admin");

    userRepository.save(admin);

    String token = loginAndGetAccessToken(adminEmail, "Password123!");
    User target = createTargetUser();

    mockMvc.perform(post("/admin/users/" + target.getId() + "/reactivate")
        .header("Authorization", "Bearer " + token))
      .andDo(print())
      .andExpect(status().isForbidden());
  }

  @Test
  void deactivateThenReactivate_areIdempotent_statusReflectsState_andAuditIsWritten() throws Exception {
    long beforeAuditCount = auditRepository.count();

    String superAdminToken = createSuperAdminAndLogin();
    User target = createTargetUser();

    mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
        .header("Authorization", "Bearer " + superAdminToken))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false));

    User deactivated = userRepository.findById(target.getId()).orElseThrow();

    assertThat(deactivated.getActive()).isFalse();

    mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
        .header("Authorization", "Bearer " + superAdminToken))
      .andDo(print())
      .andExpect(status().isOk());

    User stillDeactivated = userRepository.findById(target.getId()).orElseThrow();

    assertThat(stillDeactivated.getActive()).isFalse();

    mockMvc.perform(post("/admin/users/" + target.getId() + "/reactivate")
        .header("Authorization", "Bearer " + superAdminToken))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(true));

    User reactivated = userRepository.findById(target.getId()).orElseThrow();

    assertThat(reactivated.getActive()).isTrue();

    long afterAuditCount = auditRepository.count();

    assertThat(afterAuditCount).isGreaterThan(beforeAuditCount);
  }

  private User createTargetUser() {
    User target = new User();

    target.setEmail("target-" + UUID.randomUUID() + "@test.com");
    target.setPassword(passwordEncoder.encode("Password123!"));
    target.setRole(userRole);
    target.setActive(true);
    target.setFirstName("Target");
    target.setLastName("User");

    return userRepository.save(target);
  }

  private String createSuperAdminAndLogin() throws Exception {
    String email = "super-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    User superAdmin = new User();

    superAdmin.setEmail(email);
    superAdmin.setPassword(passwordEncoder.encode(password));
    superAdmin.setRole(superAdminRole);
    superAdmin.setActive(true);
    superAdmin.setFirstName("Super");
    superAdmin.setLastName("Admin");

    userRepository.save(superAdmin);

    return loginAndGetAccessToken(email, password);
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    AuthRequest payload = new AuthRequest(email, password);

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
