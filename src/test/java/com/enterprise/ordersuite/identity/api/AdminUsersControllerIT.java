package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired IdentityAuditEventRepository auditRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void deactivate_requiresAdmin_403ForNonAdmin() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        String nonAdminEmail = "user-" + UUID.randomUUID() + "@test.com";
        User nonAdmin = new User();
        nonAdmin.setEmail(nonAdminEmail);
        nonAdmin.setPassword(passwordEncoder.encode("Password123!"));
        nonAdmin.setRole(userRole);
        nonAdmin.setActive(true);
        nonAdmin.setFirstName("Non");
        nonAdmin.setLastName("Admin");
        userRepository.save(nonAdmin);

        String nonAdminAccessToken = loginAndGetAccessToken(nonAdminEmail, "Password123!");

        User target = createTargetUser(userRole);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + nonAdminAccessToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void reactivate_requiresAdmin_403ForNonAdmin() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        String nonAdminEmail = "user-" + UUID.randomUUID() + "@test.com";
        User nonAdmin = new User();
        nonAdmin.setEmail(nonAdminEmail);
        nonAdmin.setPassword(passwordEncoder.encode("Password123!"));
        nonAdmin.setRole(userRole);
        nonAdmin.setActive(true);
        nonAdmin.setFirstName("Non");
        nonAdmin.setLastName("Admin");
        userRepository.save(nonAdmin);

        String nonAdminAccessToken = loginAndGetAccessToken(nonAdminEmail, "Password123!");

        User target = createTargetUser(userRole);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + nonAdminAccessToken))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivateThenReactivate_areIdempotent_statusReflectsState_andAuditIsWritten() throws Exception {
        long beforeAuditCount = auditRepository.count();

        String adminAccessToken = createAdminAndLogin();

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User target = createTargetUser(userRole);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target.getId()))
                .andExpect(jsonPath("$.active").value(false));

        assertStatus(adminAccessToken, target.getId(), false);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target.getId()))
                .andExpect(jsonPath("$.active").value(false));

        assertStatus(adminAccessToken, target.getId(), false);

        long afterDeactivateAuditCount = auditRepository.count();
        assertThat(afterDeactivateAuditCount).isEqualTo(beforeAuditCount + 1);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target.getId()))
                .andExpect(jsonPath("$.active").value(true));

        assertStatus(adminAccessToken, target.getId(), true);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target.getId()))
                .andExpect(jsonPath("$.active").value(true));

        assertStatus(adminAccessToken, target.getId(), true);

        long afterReactivateAuditCount = auditRepository.count();
        assertThat(afterReactivateAuditCount).isEqualTo(beforeAuditCount + 2);
    }

    private void assertStatus(String adminAccessToken, long userId, boolean expectedActive) throws Exception {
        mockMvc.perform(get("/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.active").value(expectedActive));
    }

    private User createTargetUser(Role userRole) {
        String targetEmail = "target-" + UUID.randomUUID() + "@test.com";

        User target = new User();
        target.setEmail(targetEmail);
        target.setPassword(passwordEncoder.encode("Password123!"));
        target.setRole(userRole);
        target.setActive(true);
        target.setFirstName("Target");
        target.setLastName("User");

        return userRepository.save(target);
    }

    private String createAdminAndLogin() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        String adminEmail = "admin-" + UUID.randomUUID() + "@test.com";
        String adminPassword = "Passord123!";

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);
        admin.setActive(true);
        admin.setFirstName("Test");
        admin.setLastName("Admin");
        userRepository.save(admin);

        return loginAndGetAccessToken(adminEmail, adminPassword);
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