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
  import com.enterprise.ordersuite.storage.ObjectStorageService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;
  import org.springframework.web.multipart.MultipartFile;

  import java.io.ByteArrayInputStream;
  import java.util.UUID;

  @Service
  @RequiredArgsConstructor
  public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileMapper profileMapper;
    private final ObjectStorageService objectStorageService;
    private final AvatarValidator avatarValidator;
    private final AvatarImageProcessor avatarImageProcessor;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
      User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

      UserProfile profile = userProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

      return toProfileResponse(user, profile);
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

      return toProfileResponse(user, profile);
    }

    @Transactional
    public ProfileResponse uploadAvatar(
      Long userId,
      MultipartFile file
    ) {
      User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

      UserProfile profile = userProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

      avatarValidator.validate(file);

      byte[] webpImage =
        avatarImageProcessor.convertToWebp(file);

      String oldAvatarKey = profile.getAvatarKey();

      String newAvatarKey =
        "avatars/" + UUID.randomUUID() + ".webp";

      objectStorageService.upload(
        newAvatarKey,
        new ByteArrayInputStream(webpImage),
        webpImage.length,
        "image/webp"
      );

      profile.setAvatarKey(newAvatarKey);
      userProfileRepository.save(profile);

      if (oldAvatarKey != null) {
        objectStorageService.delete(oldAvatarKey);
      }

      return toProfileResponse(user, profile);
    }

    @Transactional
    public ProfileResponse deleteAvatar(Long userId) {
      User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

      UserProfile profile = userProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

      String avatarKey = profile.getAvatarKey();

      if (avatarKey == null) {
        return toProfileResponse(user, profile);
      }

      objectStorageService.delete(avatarKey);

      profile.setAvatarKey(null);
      userProfileRepository.save(profile);

      return toProfileResponse(user, profile);
    }

    @Transactional
    public void createProfile(Long userId) {
      UserProfile profile = new UserProfile(userId);

      userProfileRepository.save(profile);
    }

    private ProfileResponse toProfileResponse(
      User user,
      UserProfile profile
    ) {
      String avatarUrl = profile.getAvatarKey() == null
        ? null
        : objectStorageService.getUrl(profile.getAvatarKey());

      return profileMapper.toResponse(
        user,
        profile,
        avatarUrl
      );
    }
  }
