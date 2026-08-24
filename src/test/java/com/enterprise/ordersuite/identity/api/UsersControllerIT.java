package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class UsersControllerIT {

  private static final int LIST_PAGE_SIZE = 100;

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

  @Test
  void listUsers_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/users?page=0&size=" + LIST_PAGE_SIZE))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void listUsers_nonAdmin_returns403() throws Exception {
    String token = registerAndLoginUser();

    mockMvc.perform(get("/users?page=0&size=" + LIST_PAGE_SIZE)
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void listUsers_admin_returns200_withPagedPayload_andSafeFields() throws Exception {
    String adminToken = createAdminAndLogin();

    String targetEmail = registerUser();

    mockMvc.perform(get("/users?page=0&size=" + LIST_PAGE_SIZE)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isArray())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(LIST_PAGE_SIZE))
      .andExpect(jsonPath("$.totalItems").isNumber())
      .andExpect(jsonPath("$.totalPages").isNumber())
      .andExpect(jsonPath("$.items[?(@.email == '" + targetEmail + "')]").isNotEmpty())
      .andExpect(jsonPath("$.items[?(@.email == '" + targetEmail + "')][0].password").doesNotExist());
  }

  @Test
  void getUser_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/users/1"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void getUser_nonAdmin_returns403() throws Exception {
    String token = registerAndLoginUser();

    mockMvc.perform(get("/users/1")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isForbidden());
  }

  @Test
  void getUser_admin_returns200_andSafeDetail() throws Exception {
    String adminToken = createAdminAndLogin();

    User target = createTargetUser();

    mockMvc.perform(get("/users/" + target.getId())
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(target.getId()))
      .andExpect(jsonPath("$.email").value(target.getEmail()))
      .andExpect(jsonPath("$.role").value("USER"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.firstName").value("Target"))
      .andExpect(jsonPath("$.lastName").value("User"))
      .andExpect(jsonPath("$.createdAt").exists())
      .andExpect(jsonPath("$.updatedAt").exists())
      .andExpect(jsonPath("$.password").doesNotExist());
  }

  private String registerUser() throws Exception {
    String email = "target-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    RegisterRequest request = new RegisterRequest();
    request.setEmail(email);
    request.setFirstName("Target");
    request.setLastName("User");
    request.setPassword(password);

    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    return email;
  }

  private String registerAndLoginUser() throws Exception {
    String email = registerUser();

    return loginAndGetAccessToken(email, "Password123!");
  }

  private User createTargetUser() {
    Role userRole = roleRepository.findByName("USER").orElseThrow();

    String email = "target-" + UUID.randomUUID() + "@test.com";

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode("Password123!"));
    user.setRole(userRole);
    user.setActive(true);
    user.setFirstName("Target");
    user.setLastName("User");

    return userRepository.save(user);
  }

  private String createAdminAndLogin() throws Exception {
    Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

    String email = "admin-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(password));
    user.setRole(adminRole);
    user.setActive(true);
    user.setFirstName("Test");
    user.setLastName("Admin");

    userRepository.save(user);

    return loginAndGetAccessToken(email, password);
  }

  private String loginAndGetAccessToken(
    String email,
    String password
  ) throws Exception {
    AuthRequest request = new AuthRequest(email, password);

    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").exists())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("accessToken")
      .asText();
  }
}
