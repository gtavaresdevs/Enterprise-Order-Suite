package com.enterprise.ordersuite.orders.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderItemRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class OrderControllerIT {

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

  private User adminUser;
  private User regularUser;
  private User otherUser;

  private String adminToken;
  private String userToken;
  private String otherToken;

  @BeforeEach
  void setUp() throws Exception {
    adminUser = createTestUser(
      "ADMIN",
      "Admin",
      "User",
      "admin-" + UUID.randomUUID() + "@test.com"
    );

    regularUser = createTestUser(
      "USER",
      "Regular",
      "User",
      "user-" + UUID.randomUUID() + "@test.com"
    );

    otherUser = createTestUser(
      "USER",
      "Other",
      "User",
      "other-" + UUID.randomUUID() + "@test.com"
    );

    adminToken = loginAndGetAccessToken(
      adminUser.getEmail(),
      DEFAULT_PASSWORD
    );

    userToken = loginAndGetAccessToken(
      regularUser.getEmail(),
      DEFAULT_PASSWORD
    );

    otherToken = loginAndGetAccessToken(
      otherUser.getEmail(),
      DEFAULT_PASSWORD
    );
  }

  @Test
  void getOrderById_asOwner_returns200() throws Exception {
    String orderNumber = "ORD-OWN-" + UUID.randomUUID();

    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      orderNumber
    );

    mockMvc.perform(get("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + userToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.orderNumber").value(orderNumber));
  }

  @Test
  void getOrderById_notAsOwner_returns403() throws Exception {
    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-OTHER-" + UUID.randomUUID()
    );

    mockMvc.perform(get("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + otherToken))
      .andExpect(status().isForbidden());
  }

  @Test
  void getOrderById_asAdmin_returns200() throws Exception {
    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-ADMIN-" + UUID.randomUUID()
    );

    mockMvc.perform(get("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk());
  }

  @Test
  void createOrder_success_andDecrementsStock() throws Exception {
    Long productId = createProduct(
      "Order Test Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    OrderItemRequest item = OrderItemRequest.builder()
      .productId(productId)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber("ORD-STOCK-" + UUID.randomUUID())
      .customerId(adminUser.getId())
      .status(OrderStatus.PENDING)
      .items(List.of(item))
      .build();

    mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.totalAmount").value(20.00));

    assertThat(getProductStock(productId))
      .isEqualTo(8);
  }

  @Test
  void updateOrder_toCancelled_incrementsStockBack() throws Exception {
    Long productId = createProduct(
      "Cancel Test Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    OrderItemRequest item = OrderItemRequest.builder()
      .productId(productId)
      .quantity(3)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderCreateRequest createRequest = OrderCreateRequest.builder()
      .orderNumber("ORD-CANCEL-" + UUID.randomUUID())
      .customerId(adminUser.getId())
      .status(OrderStatus.PENDING)
      .items(List.of(item))
      .build();

    String createResponse = mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long orderId = objectMapper.readTree(createResponse)
      .get("id")
      .asLong();

    assertThat(getProductStock(productId))
      .isEqualTo(7);

    OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
      .status(OrderStatus.CANCELLED)
      .build();

    mockMvc.perform(put("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updateRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(getProductStock(productId))
      .isEqualTo(10);
  }

  @Test
  void updateOrder_toProcessing_returns200() throws Exception {
    Long productId = createProduct(
      "Transition Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    OrderItemRequest item = OrderItemRequest.builder()
      .productId(productId)
      .quantity(1)
      .unitPrice(new BigDecimal("100.00"))
      .build();

    OrderCreateRequest createRequest = OrderCreateRequest.builder()
      .orderNumber("ORD-TRANS-" + UUID.randomUUID())
      .customerId(adminUser.getId())
      .status(OrderStatus.PENDING)
      .items(List.of(item))
      .build();

    String response = mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long orderId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
      .status(OrderStatus.PROCESSING)
      .build();

    mockMvc.perform(put("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updateRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PROCESSING"));
  }

  @Test
  void updateOrder_invalidTransition_returns400() throws Exception {
    Long productId = createProduct(
      "Fail Transition Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    OrderItemRequest item = OrderItemRequest.builder()
      .productId(productId)
      .quantity(1)
      .unitPrice(new BigDecimal("100.00"))
      .build();

    OrderCreateRequest createRequest = OrderCreateRequest.builder()
      .orderNumber("ORD-FAIL-" + UUID.randomUUID())
      .customerId(adminUser.getId())
      .status(OrderStatus.PENDING)
      .items(List.of(item))
      .build();

    String response = mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long orderId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
      .status(OrderStatus.DELIVERED)
      .build();

    mockMvc.perform(put("/api/v1/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updateRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
  }

  @Test
  void createOrder_validationFailure_returns400() throws Exception {
    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber("AB")
      .customerId(null)
      .status(null)
      .items(List.of())
      .build();

    mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
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
    user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
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

  private Long createOrderAsUser(
    String token,
    Long customerId,
    String orderNumber
  ) throws Exception {
    Long productId = createProduct(
      "Generic Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      100
    );

    OrderItemRequest item = OrderItemRequest.builder()
      .productId(productId)
      .quantity(1)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber(orderNumber)
      .customerId(customerId)
      .status(OrderStatus.PENDING)
      .items(List.of(item))
      .build();

    String response = mockMvc.perform(post("/api/v1/orders")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("id")
      .asLong();
  }

  private Long createProduct(
    String name,
    String sku,
    BigDecimal price,
    Integer stockQuantity
  ) throws Exception {
    ProductRequest request = ProductRequest.builder()
      .name(name)
      .sku(sku)
      .price(price)
      .stockQuantity(stockQuantity)
      .build();

    String response = mockMvc.perform(post("/api/v1/products")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("id")
      .asLong();
  }

  private Integer getProductStock(Long productId) throws Exception {
    String response = mockMvc.perform(get("/api/v1/products/{id}", productId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("stockQuantity")
      .asInt();
  }
}
