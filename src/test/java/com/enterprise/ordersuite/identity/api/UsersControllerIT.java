package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UsersControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void listUsers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/users"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_nonAdmin_returns403() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        String token = createUserAndLogin(userRole);

        mockMvc.perform(get("/users?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_admin_returns200_withPagedPayload_andSafeFields() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        String adminToken = createAdminAndLogin(adminRole);

        // ensure at least one extra user exists for list content
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        createTargetUser(userRole);

        mockMvc.perform(get("/users?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").exists())
                .andExpect(jsonPath("$.totalPages").exists())

                // safe fields on first item
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].email").exists())
                .andExpect(jsonPath("$.items[0].role").exists())
                .andExpect(jsonPath("$.items[0].active").exists())
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].updatedAt").exists())

                // must not leak sensitive fields
                .andExpect(jsonPath("$.items[0].password").doesNotExist());
    }

    @Test
    void getUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_nonAdmin_returns403() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        String token = createUserAndLogin(userRole);

        mockMvc.perform(get("/users/1")
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void getUser_admin_returns200_andSafeDetail() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        String adminToken = createAdminAndLogin(adminRole);

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User target = createTargetUser(userRole);

        mockMvc.perform(get("/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
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

    private User createTargetUser(Role role) {
        String email = "target-" + UUID.randomUUID() + "@test.com";

        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password123!"));
        u.setRole(role);
        u.setActive(true);
        u.setFirstName("Target");
        u.setLastName("User");
        return userRepository.save(u);
    }

    private String createUserAndLogin(Role userRole) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        String password = "Password123!";

        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(userRole);
        u.setActive(true);
        u.setFirstName("Non");
        u.setLastName("Admin");
        userRepository.save(u);

        return loginAndGetAccessToken(email, password);
    }

    private String createAdminAndLogin(Role adminRole) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@test.com";
        String password = "Passord123!";

        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(adminRole);
        u.setActive(true);
        u.setFirstName("Test");
        u.setLastName("Admin");
        userRepository.save(u);

        return loginAndGetAccessToken(email, password);
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

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}