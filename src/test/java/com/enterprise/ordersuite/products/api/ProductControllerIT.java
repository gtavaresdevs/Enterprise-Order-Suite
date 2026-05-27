package com.enterprise.ordersuite.products.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ProductControllerIT extends AbstractPostgresRepositoryTest {

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

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        User adminUser = createTestUser("ADMIN", "Admin", "User", "admin_prod_" + System.currentTimeMillis() + "@test.com");
        User regularUser = createTestUser("USER", "Regular", "User", "user_prod_" + System.currentTimeMillis() + "@test.com");

        adminToken = loginAndGetAccessToken(adminUser.getEmail(), DEFAULT_PASSWORD);
        userToken = loginAndGetAccessToken(regularUser.getEmail(), DEFAULT_PASSWORD);
    }

    @Test
    void createProduct_admin_success() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .sku("TEST-SKU-001")
                .price(new BigDecimal("19.99"))
                .stockQuantity(100)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Test Product")))
                .andExpect(jsonPath("$.sku", is("TEST-SKU-001")));
    }

    @Test
    void createProduct_user_forbidden() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .sku("TEST-SKU-USER")
                .price(new BigDecimal("19.99"))
                .stockQuantity(100)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_admin_success() throws Exception {
        ProductRequest createRequest = ProductRequest.builder()
                .name("Original Name")
                .sku("SKU-UPDATE")
                .price(new BigDecimal("10.00"))
                .stockQuantity(50)
                .build();

        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Name")
                .sku("SKU-UPDATE")
                .price(new BigDecimal("15.00"))
                .stockQuantity(40)
                .build();

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.price", is(15.00)));
    }

    @Test
    void getProductById_user_success() throws Exception {
        ProductRequest createRequest = ProductRequest.builder()
                .name("Public Product")
                .sku("SKU-PUBLIC")
                .price(new BigDecimal("5.00"))
                .stockQuantity(10)
                .build();

        // Admin creates it first
        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        // User reads it
        mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + userToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Public Product")));
    }

    @Test
    void deleteProduct_admin_success() throws Exception {
        ProductRequest createRequest = ProductRequest.builder()
                .name("To Be Deleted")
                .sku("SKU-DELETE")
                .price(new BigDecimal("1.00"))
                .stockQuantity(1)
                .build();

        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isNotFound());
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
}
