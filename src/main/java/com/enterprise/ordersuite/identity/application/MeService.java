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
        User user = currentUserService.requireActiveUser();

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
        User user = currentUserService.requireActiveUser();

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        return new UpdateMeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUpdatedAt()
        );
    }
}
