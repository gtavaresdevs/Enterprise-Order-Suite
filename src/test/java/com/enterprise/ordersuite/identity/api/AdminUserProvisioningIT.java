package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.api.dto.AdminCreateUserRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.TestEmailServiceConfig;
import com.enterprise.ordersuite.support.TestEmailServiceConfig.CapturingEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestEmailServiceConfig.class)
class AdminUserProvisioningIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityAuditEventRepository auditRepo;

    @Autowired CapturingEmailService emailService;

    private String adminEmail;
    private String adminPassword;
    private String userEmail;
    private String userPassword;

    @BeforeEach
    void setup() {
        emailService.clear();
        auditRepo.deleteAll();

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("ADMIN");
                    return roleRepository.save(r);
                });

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("USER");
                    return roleRepository.save(r);
                });

        adminEmail = "admin." + UUID.randomUUID() + "@test.local";
        adminPassword = "AdminPass123!";
        userEmail = "user." + UUID.randomUUID() + "@test.local";
        userPassword = "UserPass123!";

        newPersistedUser(adminEmail, adminPassword, adminRole, true, "Admin", "User");
        newPersistedUser(userEmail, userPassword, userRole, true, "Normal", "User");
    }

    @Test
    void createUser_noToken_401() throws Exception {
        AdminCreateUserRequest req = new AdminCreateUserRequest(
                "new.user@test.local",
                "New",
                "User",
                null,
                true
        );

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_nonAdmin_403() throws Exception {
        String token = loginAndGetAccessToken(userEmail, userPassword);

        AdminCreateUserRequest req = new AdminCreateUserRequest(
                "new.user@test.local",
                "New",
                "User",
                null,
                true
        );

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_admin_201_persistsUser_writesAudit_andSendsEmail_byDefault() throws Exception {
        String token = loginAndGetAccessToken(adminEmail, adminPassword);

        String newEmail = "created." + UUID.randomUUID() + "@test.local";

        AdminCreateUserRequest req = new AdminCreateUserRequest(
                newEmail,
                "Created",
                "User",
                null,
                null
        );

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(true));

        User saved = userRepository.findByEmailIgnoreCase(newEmail).orElseThrow();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getFirstName()).isEqualTo("Created");
        assertThat(saved.getLastName()).isEqualTo("User");

        IdentityAuditEvent evt = auditRepo.findAll().stream()
                .filter(e -> e.getType() == IdentityAuditEventType.USER_CREATED)
                .max(Comparator.comparing(IdentityAuditEvent::getCreatedAt))
                .orElseThrow();

        assertThat(evt.getActorUserId()).isNotNull();
        assertThat(evt.getTargetUserId()).isEqualTo(saved.getId());
        assertThat(evt.getMetadata()).contains(newEmail);

        assertThat(emailService.sent()).hasSize(1);
        assertThat(emailService.sent().get(0).toEmail()).isEqualTo(newEmail);
        assertThat(emailService.sent().get(0).resetUrl()).contains("token=");
    }

    @Test
    void createUser_admin_emailFailure_doesNotFailProvisioning_still201_andWritesAudit() throws Exception {
        String token = loginAndGetAccessToken(adminEmail, adminPassword);

        emailService.failNext(true);

        String newEmail = "created.failmail." + UUID.randomUUID() + "@test.local";

        AdminCreateUserRequest req = new AdminCreateUserRequest(
                newEmail,
                "Created",
                "User",
                null,
                true
        );

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmailIgnoreCase(newEmail).orElseThrow();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getFirstName()).isEqualTo("Created");
        assertThat(saved.getLastName()).isEqualTo("User");

        assertThat(auditRepo.findAll().stream().anyMatch(e -> e.getType() == IdentityAuditEventType.USER_CREATED))
                .isTrue();

        assertThat(emailService.sent()).isEmpty();
    }

    @Test
    void resendPasswordSetup_admin_200_sendsEmail_andWritesAudit() throws Exception {
        String token = loginAndGetAccessToken(adminEmail, adminPassword);

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        String targetEmail = "target." + UUID.randomUUID() + "@test.local";

        User savedTarget = newPersistedUser(
                targetEmail,
                "TempPass123!",
                userRole,
                true,
                "Target",
                "User"
        );

        mockMvc.perform(post("/admin/users/{id}/password-setup", savedTarget.getId())
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk());

        assertThat(emailService.sent()).hasSize(1);
        assertThat(emailService.sent().get(0).toEmail()).isEqualTo(targetEmail);

        IdentityAuditEvent evt = auditRepo.findAll().stream()
                .filter(e -> e.getType() == IdentityAuditEventType.PASSWORD_SETUP_SENT)
                .max(Comparator.comparing(IdentityAuditEvent::getCreatedAt))
                .orElseThrow();

        assertThat(evt.getTargetUserId()).isEqualTo(savedTarget.getId());
        assertThat(evt.getMetadata()).contains(targetEmail);
    }

    @Test
    void resendPasswordSetup_inactiveTarget_returns400_andDoesNotSendEmail() throws Exception {
        String token = loginAndGetAccessToken(adminEmail, adminPassword);

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        String targetEmail = "inactive." + UUID.randomUUID() + "@test.local";

        User savedTarget = newPersistedUser(
                targetEmail,
                "TempPass123!",
                userRole,
                false,
                "Inactive",
                "User"
        );

        mockMvc.perform(post("/admin/users/{id}/password-setup", savedTarget.getId())
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest());

        assertThat(emailService.sent()).isEmpty();
        assertThat(auditRepo.findAll().stream().noneMatch(e -> e.getType() == IdentityAuditEventType.PASSWORD_SETUP_SENT))
                .isTrue();
    }

    private User newPersistedUser(
            String email,
            String rawPassword,
            Role role,
            boolean active,
            String firstName,
            String lastName
    ) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        u.setActive(active);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return userRepository.save(u);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String responseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseJson).get("accessToken").asText();
    }
}