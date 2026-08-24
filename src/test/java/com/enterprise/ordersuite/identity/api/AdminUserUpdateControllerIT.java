package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.api.dto.AdminUpdateUserRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
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

import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class AdminUserUpdateControllerIT {

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
  private Role adminRole;
  private Role superAdminRole;

  @BeforeEach
  void setup() {
    userRole = roleRepository.findByName("USER").orElseThrow();
    adminRole = roleRepository.findByName("ADMIN").orElseThrow();

    superAdminRole = roleRepository.findByName("SUPER_ADMIN")
      .orElseGet(() -> {
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        return roleRepository.save(role);
      });
  }

  @Test
  void updateUser_standardAdmin_403ForNonSuperAdmin() throws Exception {
    User standardAdmin = createUser(
      "admin-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      adminRole,
      true,
      "Standard",
      "Admin"
    );

    User target = createUser(
      "target-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      userRole,
      true,
      "Target",
      "User"
    );

    String token = loginAndGetAccessToken(
      standardAdmin.getEmail(),
      "Password123!"
    );

    AdminUpdateUserRequest request = new AdminUpdateUserRequest(
      "Johnny",
      "Updated",
      "johnny.updated@test.com"
    );

    mockMvc.perform(patch("/admin/users/" + target.getId())
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andDo(print())
      .andExpect(status().isForbidden());
  }

  @Test
  void updateUser_superAdmin_updatesFields_andWritesAudit() throws Exception {
    long beforeAuditCount = auditRepository.count();

    User superAdmin = createUser(
      "super-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      superAdminRole,
      true,
      "Super",
      "Admin"
    );

    User target = createUser(
      "target-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      userRole,
      true,
      "John",
      "Doe"
    );

    String token = loginAndGetAccessToken(
      superAdmin.getEmail(),
      "Password123!"
    );

    String updatedEmail =
      "johnny.doer-" + UUID.randomUUID() + "@test.com";

    AdminUpdateUserRequest request = new AdminUpdateUserRequest(
      "Johnny",
      "Doer",
      updatedEmail
    );

    mockMvc.perform(patch("/admin/users/" + target.getId())
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(target.getId()))
      .andExpect(jsonPath("$.firstName").value("Johnny"))
      .andExpect(jsonPath("$.lastName").value("Doer"))
      .andExpect(jsonPath("$.email").value(updatedEmail));

    User updated = userRepository.findById(target.getId()).orElseThrow();

    assertThat(updated.getFirstName()).isEqualTo("Johnny");
    assertThat(updated.getLastName()).isEqualTo("Doer");
    assertThat(updated.getEmail()).isEqualTo(updatedEmail);

    long afterAuditCount = auditRepository.count();

    assertThat(afterAuditCount).isEqualTo(beforeAuditCount + 1);

    IdentityAuditEvent event = auditRepository.findAll().stream()
      .filter(a -> a.getType() == IdentityAuditEventType.USER_UPDATED)
      .max(Comparator.comparing(IdentityAuditEvent::getCreatedAt))
      .orElseThrow();

    assertThat(event.getActorUserId()).isEqualTo(superAdmin.getId());
    assertThat(event.getTargetUserId()).isEqualTo(target.getId());
    assertThat(event.getMetadata()).contains("firstName");
    assertThat(event.getMetadata()).contains("lastName");
    assertThat(event.getMetadata()).contains("email");
    assertThat(event.getMetadata()).contains("John");
    assertThat(event.getMetadata()).contains("Johnny");
  }

  @Test
  void updateUser_superAdmin_sameValues_doesNotWriteAudit() throws Exception {
    long beforeAuditCount = auditRepository.count();

    User superAdmin = createUser(
      "super-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      superAdminRole,
      true,
      "Super",
      "Admin"
    );

    User target = createUser(
      "target-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      userRole,
      true,
      "John",
      "Doe"
    );

    String token = loginAndGetAccessToken(
      superAdmin.getEmail(),
      "Password123!"
    );

    AdminUpdateUserRequest request = new AdminUpdateUserRequest(
      "John",
      "Doe",
      target.getEmail()
    );

    mockMvc.perform(patch("/admin/users/" + target.getId())
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(target.getId()))
      .andExpect(jsonPath("$.firstName").value("John"))
      .andExpect(jsonPath("$.lastName").value("Doe"))
      .andExpect(jsonPath("$.email").value(target.getEmail()));

    long afterAuditCount = auditRepository.count();

    assertThat(afterAuditCount).isEqualTo(beforeAuditCount);
  }

  @Test
  void updateUser_superAdmin_duplicateEmail_returns400() throws Exception {
    User superAdmin = createUser(
      "super-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      superAdminRole,
      true,
      "Super",
      "Admin"
    );

    User target = createUser(
      "target-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      userRole,
      true,
      "John",
      "Doe"
    );

    User other = createUser(
      "other-" + UUID.randomUUID() + "@test.com",
      "Password123!",
      userRole,
      true,
      "Other",
      "User"
    );

    String token = loginAndGetAccessToken(
      superAdmin.getEmail(),
      "Password123!"
    );

    AdminUpdateUserRequest request = new AdminUpdateUserRequest(
      "Johnny",
      "Doer",
      other.getEmail()
    );

    mockMvc.perform(patch("/admin/users/" + target.getId())
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andDo(print())
      .andExpect(status().isBadRequest());

    User unchangedTarget =
      userRepository.findById(target.getId()).orElseThrow();

    assertThat(unchangedTarget.getFirstName()).isEqualTo("John");
    assertThat(unchangedTarget.getLastName()).isEqualTo("Doe");
    assertThat(unchangedTarget.getEmail()).isNotEqualTo(other.getEmail());
  }

  private User createUser(
    String email,
    String rawPassword,
    Role role,
    boolean active,
    String firstName,
    String lastName
  ) {
    User user = new User();

    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user.setActive(active);
    user.setFirstName(firstName);
    user.setLastName(lastName);

    return userRepository.save(user);
  }

  private String loginAndGetAccessToken(
    String email,
    String password
  ) throws Exception {
    AuthRequest payload = new AuthRequest(email, password);

    var result = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(payload)))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").exists())
      .andReturn();

    String body = result.getResponse().getContentAsString();

    return objectMapper
      .readTree(body)
      .get("accessToken")
      .asText();
  }
}
