package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.auth.service.PasswordResetService;
import com.enterprise.ordersuite.identity.api.dto.*;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Slf4j
@Service
public class UserAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final IdentityAuditService identityAuditService;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CurrentUserService currentUserService,
            IdentityAuditService identityAuditService,
            PasswordResetService passwordResetService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.currentUserService = currentUserService;
        this.identityAuditService = identityAuditService;
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserStatusResponse deactivateUser(long targetUserId) {
        User actor = currentUserService.requireActiveUser();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(target.getActive())) {
            target.setActive(false);

            identityAuditService.recordEvent(
                    IdentityAuditEventType.USER_DEACTIVATED,
                    actor.getId(),
                    target.getId(),
                    null
            );
        }

        return new UserStatusResponse(target.getId(), target.getActive());
    }

    @Transactional
    public UserStatusResponse reactivateUser(long targetUserId) {
        User actor = currentUserService.requireActiveUser();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.FALSE.equals(target.getActive())) {
            target.setActive(true);

            identityAuditService.recordEvent(
                    IdentityAuditEventType.USER_REACTIVATED,
                    actor.getId(),
                    target.getId(),
                    null
            );
        }

        return new UserStatusResponse(target.getId(), target.getActive());
    }

    @Transactional(readOnly = true)
    public UserStatusResponse getStatus(long targetUserId) {
        currentUserService.requireActiveUser();

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return new UserStatusResponse(user.getId(), user.getActive());
    }

    @Transactional
    public UserStatusResponse setUserRole(long targetUserId, SetUserRoleRequest request) {
        User actor = currentUserService.requireActiveUser();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Role newRole = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        String previousRole = target.getRole().getName();
        String nextRole = newRole.getName();

        if (!previousRole.equals(nextRole)) {
            target.setRole(newRole);

            identityAuditService.recordEvent(
                    IdentityAuditEventType.USER_ROLE_CHANGED,
                    actor.getId(),
                    target.getId(),
                    "{\"from\":\"" + previousRole + "\",\"to\":\"" + nextRole + "\"}"
            );
        }

        return new UserStatusResponse(target.getId(), target.getActive());
    }

    @Transactional
    public AdminCreateUserResponse createUser(AdminCreateUserRequest request) {
        User actor = currentUserService.requireActiveUser();

        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        String roleName = (request.role() == null || request.role().isBlank())
                ? "USER"
                : request.role().trim().toUpperCase(Locale.ROOT);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(role);
        user.setActive(true);

        user.setPassword(passwordEncoder.encode(generateRandomPassword()));

        User saved = userRepository.save(user);

        identityAuditService.recordEvent(
                IdentityAuditEventType.USER_CREATED,
                actor.getId(),
                saved.getId(),
                "{\"email\":\"" + saved.getEmail() + "\",\"role\":\"" + saved.getRole().getName() + "\"}"
        );

        boolean send = request.sendPasswordSetupEmail() == null || request.sendPasswordSetupEmail();
        if (send) {
            String emailToSend = saved.getEmail();
            Long savedUserId = saved.getId();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        passwordResetService.sendPasswordSetupForNewUser(emailToSend);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to send password setup email for userId={}, email={}",
                                savedUserId,
                                emailToSend,
                                e
                        );
                    }
                }
            });
        }

        return new AdminCreateUserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole().getName(),
                saved.getActive(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public void sendPasswordSetup(long targetUserId) {
        User actor = currentUserService.requireActiveUser();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!Boolean.TRUE.equals(target.getActive())) {
            throw new IllegalArgumentException("User is inactive");
        }

        try {
            passwordResetService.sendPasswordSetupForNewUser(target.getEmail());
        } catch (Exception e) {
            log.warn(
                    "Failed to send password setup email for userId={}, email={}",
                    target.getId(),
                    target.getEmail(),
                    e
            );
        }

        identityAuditService.recordEvent(
                IdentityAuditEventType.PASSWORD_SETUP_SENT,
                actor.getId(),
                target.getId(),
                "{\"email\":\"" + target.getEmail() + "\"}"
        );
    }

    @Transactional
    public AdminUpdateUserResponse updateUser(long targetUserId, AdminUpdateUserRequest request) {
        User actor = currentUserService.requireActiveUser();

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String previousFirstName = target.getFirstName();
        String previousLastName = target.getLastName();
        String previousEmail = target.getEmail();

        boolean changed = false;
        StringBuilder changedFields = new StringBuilder();

        if (hasText(request.firstName())) {
            String newFirstName = request.firstName().trim();
            if (!newFirstName.equals(previousFirstName)) {
                target.setFirstName(newFirstName);
                appendChangedField(changedFields, "firstName", previousFirstName, newFirstName);
                changed = true;
            }
        }

        if (hasText(request.lastName())) {
            String newLastName = request.lastName().trim();
            if (!newLastName.equals(previousLastName)) {
                target.setLastName(newLastName);
                appendChangedField(changedFields, "lastName", previousLastName, newLastName);
                changed = true;
            }
        }

        if (hasText(request.email())) {
            String newEmail = normalizeEmail(request.email());
            if (!newEmail.equalsIgnoreCase(previousEmail)) {
                if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                    throw new IllegalArgumentException("Email already exists");
                }
                target.setEmail(newEmail);
                appendChangedField(changedFields, "email", previousEmail, newEmail);
                changed = true;
            }
        }

        if (changed) {
            identityAuditService.recordEvent(
                    IdentityAuditEventType.USER_UPDATED,
                    actor.getId(),
                    target.getId(),
                    "{\"changedFields\":{" + changedFields + "}}"
            );
        }

        return new AdminUpdateUserResponse(
                target.getId(),
                target.getFirstName(),
                target.getLastName(),
                target.getEmail(),
                target.getUpdatedAt()
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void appendChangedField(StringBuilder builder, String fieldName, String from, String to) {
        if (!builder.isEmpty()) {
            builder.append(",");
        }

        builder.append("\"")
                .append(fieldName)
                .append("\":{")
                .append("\"from\":\"").append(escapeJson(from)).append("\",")
                .append("\"to\":\"").append(escapeJson(to)).append("\"")
                .append("}");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}