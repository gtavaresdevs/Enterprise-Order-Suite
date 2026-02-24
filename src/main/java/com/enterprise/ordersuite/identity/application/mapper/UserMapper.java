package com.enterprise.ordersuite.identity.application.mapper;

import com.enterprise.ordersuite.identity.api.dto.UserDetailResponse;
import com.enterprise.ordersuite.identity.api.dto.UserSummaryResponse;
import com.enterprise.ordersuite.identity.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserSummaryResponse toSummary(User u) {
        return new UserSummaryResponse(
                u.getId(),
                u.getEmail(),
                u.getRole().getName(),
                u.getActive(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }

    public UserDetailResponse toDetail(User u) {
        return new UserDetailResponse(
                u.getId(),
                u.getEmail(),
                u.getRole().getName(),
                u.getActive(),
                u.getFirstName(),
                u.getLastName(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}