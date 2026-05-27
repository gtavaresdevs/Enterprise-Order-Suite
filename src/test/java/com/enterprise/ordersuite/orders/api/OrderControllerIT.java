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
import com.enterprise.ordersuite.repository.AbstractPostgresRepositoryTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class OrderControllerIT extends AbstractPostgresRepositoryTest {

    private static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private User adminUser;
    private User regularUser;
    private User otherUser;

    private String adminToken;
    private String userToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Dynamically create users directly in the database to ensure they exist for the tests
        adminUser = createTestUser("ADMIN", "Admin", "User", "admin_" + System.currentTimeMillis() + "@test.com");
        regularUser = createTestUser("USER", "Regular", "User", "user_" + System.currentTimeMillis() + "@test.com");
        otherUser = createTestUser("USER", "Other", "User", "other_" + System.currentTimeMillis() + "@test.com");

        adminToken = loginAndGetAccessToken(adminUser.getEmail(), DEFAULT_PASSWORD);
        userToken = loginAndGetAccessToken(regularUser.getEmail(), DEFAULT_PASSWORD);
        otherToken = loginAndGetAccessToken(otherUser.getEmail(), DEFAULT_PASSWORD);
    }

    @Test
    void getOrderById_asOwner_success() throws Exception {
        Long orderId = createOrderAsUser(userToken, "ORD-OWN-1");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber", is("ORD-OWN-1")));
    }

    @Test
    void getOrderById_notAsOwner_forbidden() throws Exception {
        Long orderId = createOrderAsUser(userToken, "ORD-OTHER-1");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrderById_asAdmin_alwaysSuccess() throws Exception {
        Long orderId = createOrderAsUser(userToken, "ORD-ADMIN-1");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void createOrder_success_andDecrementsStock() throws Exception {
        Long productId = createProduct("Order Test Product", "ORD-TEST-1", new BigDecimal("10.0"), 10);

        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .build();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber("ORD-STOCK-1")
                .customerId(adminUser.getId())
                .status(OrderStatus.PENDING)
                .items(List.of(item1))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber", is("ORD-STOCK-1")))
                .andExpect(jsonPath("$.totalAmount", is(20.00)));

        assertThat(getProductStock(productId)).isEqualTo(8);
    }

    @Test
    void updateOrder_toCancelled_incrementsStockBack() throws Exception {
        Long productId = createProduct("Cancel Test Product", "ORD-TEST-3", new BigDecimal("10.0"), 10);

        OrderItemRequest item = OrderItemRequest.builder().productId(productId).quantity(3).unitPrice(new BigDecimal("10.0")).build();
        OrderCreateRequest createRequest = OrderCreateRequest.builder()
                .orderNumber("ORD-CANCEL-1").customerId(adminUser.getId()).status(OrderStatus.PENDING).items(List.of(item))
                .build();

        String createResponse = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(createResponse).get("id").asLong();

        assertThat(getProductStock(productId)).isEqualTo(7);

        OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
                .status(OrderStatus.CANCELLED)
                .build();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(getProductStock(productId)).isEqualTo(10);
    }

    @Test
    void updateOrder_successTransition() throws Exception {
        Long productId = createProduct("Transition Product", "ORD-TEST-4", new BigDecimal("10.0"), 10);
        OrderItemRequest item = OrderItemRequest.builder().productId(productId).quantity(1).unitPrice(new BigDecimal("100.0")).build();
        OrderCreateRequest createRequest = OrderCreateRequest.builder()
                .orderNumber("ORD-TRANS-001").customerId(adminUser.getId()).status(OrderStatus.PENDING).items(List.of(item))
                .build();

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).get("id").asLong();

        OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
                .status(OrderStatus.PROCESSING)
                .build();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROCESSING")));
    }

    @Test
    void updateOrder_invalidTransition_fails() throws Exception {
        Long productId = createProduct("Fail Transition Product", "ORD-TEST-5", new BigDecimal("10.0"), 10);
        OrderItemRequest item = OrderItemRequest.builder().productId(productId).quantity(1).unitPrice(new BigDecimal("100.0")).build();
        OrderCreateRequest createRequest = OrderCreateRequest.builder()
                .orderNumber("ORD-TRANS-FAIL").customerId(adminUser.getId()).status(OrderStatus.PENDING).items(List.of(item))
                .build();

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).get("id").asLong();

        OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
                .status(OrderStatus.DELIVERED)
                .build();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    void createOrder_validationFailure() throws Exception {
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber("")
                .customerId(null)
                .status(null)
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT")));
    }

    private User createTestUser(String roleName, String firstName, String lastName, String email) {
        Role role = roleRepository.findByName(roleName).orElseThrow();

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRole(role);
        user.setActive(true);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        return userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        var payload = new AuthRequest(email, password);

        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private Long createOrderAsUser(String token, String orderNumber) throws Exception {
        Long userId = null;
        if (token.equals(userToken)) userId = regularUser.getId();
        else if (token.equals(adminToken)) userId = adminUser.getId();
        else if (token.equals(otherToken)) userId = otherUser.getId();

        Long productId = createProduct("Generic Product", "SKU-" + UUID.randomUUID(), new BigDecimal("10.0"), 100);

        OrderItemRequest item = OrderItemRequest.builder()
                .productId(productId)
                .quantity(1)
                .unitPrice(new BigDecimal("10.0"))
                .build();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber(orderNumber)
                .customerId(userId)
                .status(OrderStatus.PENDING)
                .items(List.of(item))
                .build();

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createProduct(String name, String sku, BigDecimal price, Integer stockQuantity) throws Exception {
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
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Integer getProductStock(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("stockQuantity").asInt();
    }
}
