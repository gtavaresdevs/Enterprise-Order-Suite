package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.profile.api.dto.ProfileResponse;
import com.enterprise.ordersuite.profile.api.dto.UpdateProfileRequest;
import com.enterprise.ordersuite.profile.application.mapper.ProfileMapper;
import com.enterprise.ordersuite.profile.domain.UserProfile;
import com.enterprise.ordersuite.profile.domain.exception.ProfileNotFoundException;
import com.enterprise.ordersuite.profile.domain.exception.UserNotFoundException;
import com.enterprise.ordersuite.profile.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

  private final UserRepository userRepository;
  private final UserProfileRepository userProfileRepository;
  private final ProfileMapper profileMapper;

  @Transactional(readOnly = true)
  public ProfileResponse getProfile(Long userId) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new UserNotFoundException(userId));

    UserProfile profile = userProfileRepository.findByUserId(userId)
      .orElseThrow(() -> new ProfileNotFoundException(userId));

    return profileMapper.toResponse(user, profile);
  }

  @Transactional
  public ProfileResponse updateProfile(
    Long userId,
    UpdateProfileRequest request
  ) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new UserNotFoundException(userId));

    UserProfile profile = userProfileRepository.findByUserId(userId)
      .orElseThrow(() -> new ProfileNotFoundException(userId));

    profile.update(
      request.phone(),
      request.country(),
      request.timezone(),
      request.department(),
      request.office(),
      request.bio()
    );

    userProfileRepository.save(profile);

    return profileMapper.toResponse(user, profile);
  }

  @Transactional
  public void createProfile(Long userId) {
    UserProfile profile = new UserProfile(userId);

    userProfileRepository.save(profile);
  }
}
