package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.SetUserRoleRequest;
import com.enterprise.ordersuite.identity.api.dto.UserStatusResponse;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;


@Service
public class UserAdminService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final IdentityAuditService identityAuditService;

    public UserAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CurrentUserService currentUserService,
            IdentityAuditService identityAuditService

    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.currentUserService = currentUserService;
        this.identityAuditService = identityAuditService;
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
        var actor = currentUserService.requireActiveUser();

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
}