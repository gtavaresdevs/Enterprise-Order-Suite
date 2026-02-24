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
class IdentityAuditControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void listAudit_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/identity-audit"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAudit_nonAdmin_returns403() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        String userToken = createUserAndLogin(userRole);

        mockMvc.perform(get("/admin/identity-audit?page=0&size=10")
                        .header("Authorization", "Bearer " + userToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void listAudit_admin_returns200_andContainsEventsAfterDeactivate() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        String adminToken = createAdminAndLogin(adminRole);

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User target = createTargetUser(userRole);

        // generate an audit event (deactivate)
        mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk());

        // query audit endpoint
        mockMvc.perform(get("/admin/identity-audit?page=0&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").exists())
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].type").exists())
                .andExpect(jsonPath("$.items[0].actorUserId").exists())
                .andExpect(jsonPath("$.items[0].targetUserId").exists())
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].targetUserId").value(target.getId()));
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