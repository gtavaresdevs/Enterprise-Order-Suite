package com.enterprise.ordersuite.profile.application.mapper;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.profile.api.dto.ProfileResponse;
import com.enterprise.ordersuite.profile.domain.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

  public ProfileResponse toResponse(
    User user,
    UserProfile profile,
    String avatarUrl
  ) {
    return new ProfileResponse(
      user.getId(),
      user.getEmail(),
      user.getFirstName(),
      user.getLastName(),
      user.getRole().getName(),
      profile.getPhone(),
      profile.getCountry(),
      profile.getTimezone(),
      profile.getDepartment(),
      profile.getOffice(),
      profile.getBio(),
      avatarUrl,
      profile.getCreatedAt(),
      profile.getUpdatedAt()
    );
  }
}
