package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.identity.domain.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserProfileRepository userProfileRepository;

  @Mock
  private ProfileMapper profileMapper;

  @Mock
  private ObjectStorageService objectStorageService;

  @Mock
  private AvatarValidator avatarValidator;

  @Mock
  private AvatarImageProcessor avatarImageProcessor;

  @InjectMocks
  private ProfileService profileService;

  private User user;
  private UserProfile profile;

  @BeforeEach
  void setup() {
    user = new User();
    user.setId(1L);
    user.setEmail("user@test.com");
    user.setFirstName("John");
    user.setLastName("Doe");
    user.setActive(true);

    Role role = new Role();
    role.setName("USER");
    user.setRole(role);

    profile = new UserProfile(1L);
    profile.setId(10L);
    profile.setPhone("+1 555 123 4567");
    profile.setCountry("United States");
    profile.setTimezone("America/New_York");
    profile.setDepartment("Operations");
    profile.setOffice("New York");
    profile.setBio("Operations manager");
  }

  @Test
  void getProfile_ExistingUserAndProfile_ReturnsProfileResponse() {

    ProfileResponse expectedResponse = profileResponse(null);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(user, profile, null))
      .thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.getProfile(1L);

    assertThat(result)
      .isEqualTo(expectedResponse);

    verify(userRepository)
      .findById(1L);

    verify(userProfileRepository)
      .findByUserId(1L);

    verify(profileMapper)
      .toResponse(user, profile, null);

    verifyNoInteractions(objectStorageService);
  }

  @Test
  void getProfile_WithAvatar_ReturnsAvatarUrl() {

    String avatarKey =
      "avatars/existing-avatar.webp";

    String avatarUrl =
      "http://localhost:9000/eos-storage/"
        + avatarKey;

    profile.setAvatarKey(avatarKey);

    ProfileResponse expectedResponse =
      profileResponse(avatarUrl);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(objectStorageService.getUrl(avatarKey))
      .thenReturn(avatarUrl);

    when(profileMapper.toResponse(
      user,
      profile,
      avatarUrl
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.getProfile(1L);

    assertThat(result)
      .isEqualTo(expectedResponse);

    verify(objectStorageService)
      .getUrl(avatarKey);

    verify(profileMapper)
      .toResponse(
        user,
        profile,
        avatarUrl
      );
  }

  @Test
  void getProfile_UserDoesNotExist_ThrowsUserNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.getProfile(1L)
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage("User not found with ID: 1");

    verify(userRepository)
      .findById(1L);

    verifyNoInteractions(
      userProfileRepository,
      profileMapper,
      objectStorageService
    );
  }

  @Test
  void getProfile_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.getProfile(1L)
    )
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage(
        "Profile not found for user with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verify(userProfileRepository)
      .findByUserId(1L);

    verifyNoInteractions(
      profileMapper,
      objectStorageService
    );
  }

  @Test
  void updateProfile_ExistingUserAndProfile_UpdatesAndReturnsProfile() {

    UpdateProfileRequest request =
      new UpdateProfileRequest(
        "+55 62 99999-9999",
        "Brazil",
        "America/Sao_Paulo",
        "Engineering",
        "Goiania",
        "Senior backend engineer"
      );

    ProfileResponse expectedResponse =
      profileResponse(null);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(
      user,
      profile,
      null
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.updateProfile(
        1L,
        request
      );

    assertThat(profile.getPhone())
      .isEqualTo("+55 62 99999-9999");

    assertThat(profile.getCountry())
      .isEqualTo("Brazil");

    assertThat(profile.getTimezone())
      .isEqualTo("America/Sao_Paulo");

    assertThat(profile.getDepartment())
      .isEqualTo("Engineering");

    assertThat(profile.getOffice())
      .isEqualTo("Goiania");

    assertThat(profile.getBio())
      .isEqualTo("Senior backend engineer");

    assertThat(result)
      .isEqualTo(expectedResponse);

    verify(userProfileRepository)
      .save(profile);

    verify(profileMapper)
      .toResponse(
        user,
        profile,
        null
      );

    verifyNoInteractions(objectStorageService);
  }

  @Test
  void updateProfile_UserDoesNotExist_ThrowsUserNotFoundException() {

    UpdateProfileRequest request =
      new UpdateProfileRequest(
        "123",
        "Brazil",
        "America/Sao_Paulo",
        "Engineering",
        "Goiania",
        "Bio"
      );

    when(userRepository.findById(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.updateProfile(
        1L,
        request
      )
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage(
        "User not found with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verifyNoInteractions(
      userProfileRepository,
      profileMapper,
      objectStorageService
    );
  }

  @Test
  void updateProfile_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    UpdateProfileRequest request =
      new UpdateProfileRequest(
        "123",
        "Brazil",
        "America/Sao_Paulo",
        "Engineering",
        "Goiania",
        "Bio"
      );

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.updateProfile(
        1L,
        request
      )
    )
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage(
        "Profile not found for user with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verify(userProfileRepository)
      .findByUserId(1L);

    verifyNoInteractions(
      profileMapper,
      objectStorageService
    );

    verify(userProfileRepository, never())
      .save(any(UserProfile.class));
  }

  @Test
  void uploadAvatar_ValidFile_UploadsProcessedImageAndUpdatesProfile() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[] {1, 2, 3}
      );

    byte[] webpImage =
      new byte[] {10, 20, 30, 40};

    String avatarKey =
      "avatars/generated.webp";

    String avatarUrl =
      "http://localhost:9000/eos-storage/"
        + avatarKey;

    ProfileResponse expectedResponse =
      profileResponse(avatarUrl);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(avatarImageProcessor.convertToWebp(file))
      .thenReturn(webpImage);

    when(objectStorageService.upload(
      any(String.class),
      any(ByteArrayInputStream.class),
      anyLong(),
      eq("image/webp")
    )).thenReturn(avatarKey);

    when(objectStorageService.getUrl(any(String.class)))
      .thenReturn(avatarUrl);

    when(profileMapper.toResponse(
      user,
      profile,
      avatarUrl
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.uploadAvatar(
        1L,
        file
      );

    assertThat(result)
      .isEqualTo(expectedResponse);

    assertThat(profile.getAvatarKey())
      .isEqualTo(avatarKey);

    verify(avatarValidator)
      .validate(file);

    verify(avatarImageProcessor)
      .convertToWebp(file);

    verify(objectStorageService)
      .upload(
        eq(avatarKey),
        any(ByteArrayInputStream.class),
        eq((long) webpImage.length),
        eq("image/webp")
      );

    verify(userProfileRepository)
      .save(profile);

    verify(profileMapper)
      .toResponse(
        user,
        profile,
        avatarUrl
      );

    verify(objectStorageService)
      .getUrl(avatarKey);

    verify(objectStorageService, never())
      .delete(any(String.class));
  }

  @Test
  void uploadAvatar_ReplacesExistingAvatar_DeletesOldAvatar() {

    String oldAvatarKey =
      "avatars/old-avatar.webp";

    String newAvatarKey =
      "avatars/new-avatar.webp";

    profile.setAvatarKey(oldAvatarKey);

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[] {1, 2, 3}
      );

    byte[] webpImage =
      new byte[] {10, 20, 30};

    String avatarUrl =
      "http://localhost:9000/eos-storage/"
        + newAvatarKey;

    ProfileResponse expectedResponse =
      profileResponse(avatarUrl);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(avatarImageProcessor.convertToWebp(file))
      .thenReturn(webpImage);

    when(objectStorageService.upload(
      any(String.class),
      any(ByteArrayInputStream.class),
      anyLong(),
      eq("image/webp")
    )).thenReturn(newAvatarKey);

    when(objectStorageService.getUrl(newAvatarKey))
      .thenReturn(avatarUrl);

    when(profileMapper.toResponse(
      user,
      profile,
      avatarUrl
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.uploadAvatar(
        1L,
        file
      );

    assertThat(result)
      .isEqualTo(expectedResponse);

    assertThat(profile.getAvatarKey())
      .isEqualTo(newAvatarKey);

    verify(objectStorageService)
      .delete(oldAvatarKey);

    verify(objectStorageService)
      .upload(
        eq(newAvatarKey),
        any(ByteArrayInputStream.class),
        eq((long) webpImage.length),
        eq("image/webp")
      );

    verify(userProfileRepository)
      .save(profile);
  }

  @Test
  void uploadAvatar_UserDoesNotExist_ThrowsUserNotFoundException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[] {1, 2, 3}
      );

    when(userRepository.findById(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.uploadAvatar(
        1L,
        file
      )
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage(
        "User not found with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verifyNoInteractions(
      userProfileRepository,
      profileMapper,
      objectStorageService,
      avatarValidator,
      avatarImageProcessor
    );
  }

  @Test
  void uploadAvatar_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[] {1, 2, 3}
      );

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.uploadAvatar(
        1L,
        file
      )
    )
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage(
        "Profile not found for user with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verify(userProfileRepository)
      .findByUserId(1L);

    verifyNoInteractions(
      profileMapper,
      objectStorageService,
      avatarValidator,
      avatarImageProcessor
    );
  }

  @Test
  void uploadAvatar_InvalidFile_DoesNotProcessOrUpload() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.txt",
        "text/plain",
        "invalid".getBytes()
      );

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    RuntimeException exception =
      new RuntimeException("Invalid avatar");

    org.mockito.Mockito
      .doThrow(exception)
      .when(avatarValidator)
      .validate(file);

    assertThatThrownBy(
      () -> profileService.uploadAvatar(
        1L,
        file
      )
    )
      .isSameAs(exception);

    verify(avatarValidator)
      .validate(file);

    verify(avatarImageProcessor, never())
      .convertToWebp(any(MultipartFile.class));

    verifyNoInteractions(objectStorageService);

    verify(userProfileRepository, never())
      .save(any(UserProfile.class));
  }

  @Test
  void deleteAvatar_ExistingAvatar_DeletesObjectAndClearsKey() {

    String avatarKey =
      "avatars/avatar.webp";

    profile.setAvatarKey(avatarKey);

    ProfileResponse expectedResponse =
      profileResponse(null);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(
      user,
      profile,
      null
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.deleteAvatar(1L);

    assertThat(result)
      .isEqualTo(expectedResponse);

    assertThat(profile.getAvatarKey())
      .isNull();

    verify(objectStorageService)
      .delete(avatarKey);

    verify(userProfileRepository)
      .save(profile);

    verify(profileMapper)
      .toResponse(
        user,
        profile,
        null
      );

    verify(objectStorageService, never())
      .getUrl(avatarKey);
  }

  @Test
  void deleteAvatar_NoAvatar_ReturnsProfileWithoutStorageInteraction() {

    profile.setAvatarKey(null);

    ProfileResponse expectedResponse =
      profileResponse(null);

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(
      user,
      profile,
      null
    )).thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.deleteAvatar(1L);

    assertThat(result)
      .isEqualTo(expectedResponse);

    verify(profileMapper)
      .toResponse(
        user,
        profile,
        null
      );

    verifyNoInteractions(
      objectStorageService
    );

    verify(userProfileRepository, never())
      .save(any(UserProfile.class));
  }

  @Test
  void deleteAvatar_UserDoesNotExist_ThrowsUserNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.deleteAvatar(1L)
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage(
        "User not found with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verifyNoInteractions(
      userProfileRepository,
      profileMapper,
      objectStorageService
    );
  }

  @Test
  void deleteAvatar_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(
      () -> profileService.deleteAvatar(1L)
    )
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage(
        "Profile not found for user with ID: 1"
      );

    verify(userRepository)
      .findById(1L);

    verify(userProfileRepository)
      .findByUserId(1L);

    verifyNoInteractions(
      objectStorageService,
      profileMapper
    );
  }

  @Test
  void createProfile_ValidUserId_SavesProfile() {

    profileService.createProfile(1L);

    ArgumentCaptor<UserProfile> captor =
      ArgumentCaptor.forClass(UserProfile.class);

    verify(userProfileRepository)
      .save(captor.capture());

    UserProfile savedProfile =
      captor.getValue();

    assertThat(savedProfile.getUserId())
      .isEqualTo(1L);

    assertThat(savedProfile.getPhone())
      .isNull();

    assertThat(savedProfile.getCountry())
      .isNull();

    assertThat(savedProfile.getTimezone())
      .isNull();

    assertThat(savedProfile.getDepartment())
      .isNull();

    assertThat(savedProfile.getOffice())
      .isNull();

    assertThat(savedProfile.getBio())
      .isNull();

    assertThat(savedProfile.getAvatarKey())
      .isNull();

    verifyNoInteractions(
      userRepository,
      profileMapper,
      objectStorageService,
      avatarValidator,
      avatarImageProcessor
    );
  }

  private ProfileResponse profileResponse(
    String avatarUrl
  ) {
    return new ProfileResponse(
      1L,
      "user@test.com",
      "John",
      "Doe",
      "USER",
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
