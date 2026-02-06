package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.MeResponse;
import com.enterprise.ordersuite.identity.api.dto.UpdateMeRequest;
import com.enterprise.ordersuite.identity.api.dto.UpdateMeResponse;
import com.enterprise.ordersuite.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

    private final CurrentUserService currentUserService;

    public MeService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe() {
        currentUserService.requireActive();
        User user = currentUserService.getUser();

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().getName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Transactional
    public UpdateMeResponse updateMe(UpdateMeRequest request) {
        currentUserService.requireActive();
        User user = currentUserService.getUser();

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        // No explicit save required if User is managed in a transactional persistence context.
        // If your CurrentUserService returns a detached entity, we will adjust to save via repository.

        return new UpdateMeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUpdatedAt()
        );
    }
}
