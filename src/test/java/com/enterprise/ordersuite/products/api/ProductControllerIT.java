package com.enterprise.ordersuite.products.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.products.api.dto.ProductRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ProductControllerIT {

  private static final String TEST_PASSWORD = "Password123!";

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

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() throws Exception {
    User adminUser = createTestUser(
      "ADMIN",
      "Admin",
      "User",
      "admin-" + UUID.randomUUID() + "@test.com"
    );

    User regularUser = createTestUser(
      "USER",
      "Regular",
      "User",
      "user-" + UUID.randomUUID() + "@test.com"
    );

    adminToken = loginAndGetAccessToken(
      adminUser.getEmail(),
      TEST_PASSWORD
    );

    userToken = loginAndGetAccessToken(
      regularUser.getEmail(),
      TEST_PASSWORD
    );
  }

  @Test
  void createProduct_asAdmin_returns201() throws Exception {
    ProductRequest request = ProductRequest.builder()
      .name("Test Product")
      .sku("TEST-SKU-" + UUID.randomUUID())
      .price(new BigDecimal("19.99"))
      .stockQuantity(100)
      .build();

    mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name", is("Test Product")))
      .andExpect(jsonPath("$.sku", is(request.getSku())));
  }

  @Test
  void createProduct_asUser_returns403() throws Exception {
    ProductRequest request = ProductRequest.builder()
      .name("Test Product")
      .sku("TEST-SKU-" + UUID.randomUUID())
      .price(new BigDecimal("19.99"))
      .stockQuantity(100)
      .build();

    mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + userToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isForbidden());
  }

  @Test
  void updateProduct_asAdmin_returns200() throws Exception {
    ProductRequest createRequest = ProductRequest.builder()
      .name("Original Name")
      .sku("SKU-" + UUID.randomUUID())
      .price(new BigDecimal("10.00"))
      .stockQuantity(50)
      .build();

    String response = mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long productId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    ProductRequest updateRequest = ProductRequest.builder()
      .name("Updated Name")
      .sku(createRequest.getSku())
      .price(new BigDecimal("15.00"))
      .stockQuantity(40)
      .build();

    mockMvc.perform(put("/api/v1/products/{id}", productId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updateRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name", is("Updated Name")))
      .andExpect(jsonPath("$.price", is(15.00)));
  }

  @Test
  void getProductById_asUser_returns200() throws Exception {
    ProductRequest createRequest = ProductRequest.builder()
      .name("Public Product")
      .sku("SKU-" + UUID.randomUUID())
      .price(new BigDecimal("5.00"))
      .stockQuantity(10)
      .build();

    String response = mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long productId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    mockMvc.perform(get("/api/v1/products/{id}", productId)
        .header("Authorization", "Bearer " + userToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name", is("Public Product")));
  }

  @Test
  void deleteProduct_asAdmin_returns204() throws Exception {
    ProductRequest createRequest = ProductRequest.builder()
      .name("To Be Deleted")
      .sku("SKU-" + UUID.randomUUID())
      .price(new BigDecimal("1.00"))
      .stockQuantity(1)
      .build();

    String response = mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long productId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    mockMvc.perform(delete("/api/v1/products/{id}", productId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/products/{id}", productId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isNotFound());
  }

  private User createTestUser(
    String roleName,
    String firstName,
    String lastName,
    String email
  ) {
    Role role = roleRepository.findByName(roleName)
      .orElseThrow();

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
    user.setRole(role);
    user.setActive(true);
    user.setFirstName(firstName);
    user.setLastName(lastName);

    return userRepository.save(user);
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
