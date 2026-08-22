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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    ProfileResponse expectedResponse = new ProfileResponse(
      1L,
      "user@test.com",
      "John",
      "Doe",
      "USER",
      "+1 555 123 4567",
      "United States",
      "America/New_York",
      "Operations",
      "New York",
      "Operations manager",
      profile.getCreatedAt(),
      profile.getUpdatedAt()
    );

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(user, profile))
      .thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.getProfile(1L);

    assertThat(result).isEqualTo(expectedResponse);

    verify(userRepository).findById(1L);
    verify(userProfileRepository).findByUserId(1L);
    verify(profileMapper).toResponse(user, profile);
  }

  @Test
  void getProfile_UserDoesNotExist_ThrowsUserNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getProfile(1L))
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage("User not found with ID: 1");

    verify(userRepository).findById(1L);
    verifyNoInteractions(userProfileRepository, profileMapper);
  }

  @Test
  void getProfile_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getProfile(1L))
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage("Profile not found for user with ID: 1");

    verify(userRepository).findById(1L);
    verify(userProfileRepository).findByUserId(1L);
    verifyNoInteractions(profileMapper);
  }

  @Test
  void updateProfile_ExistingUserAndProfile_UpdatesAndReturnsProfile() {

    UpdateProfileRequest request = new UpdateProfileRequest(
      "+55 62 99999-9999",
      "Brazil",
      "America/Sao_Paulo",
      "Engineering",
      "Goiania",
      "Senior backend engineer"
    );

    ProfileResponse expectedResponse = new ProfileResponse(
      1L,
      "user@test.com",
      "John",
      "Doe",
      "USER",
      "+55 62 99999-9999",
      "Brazil",
      "America/Sao_Paulo",
      "Engineering",
      "Goiania",
      "Senior backend engineer",
      profile.getCreatedAt(),
      profile.getUpdatedAt()
    );

    when(userRepository.findById(1L))
      .thenReturn(Optional.of(user));

    when(userProfileRepository.findByUserId(1L))
      .thenReturn(Optional.of(profile));

    when(profileMapper.toResponse(user, profile))
      .thenReturn(expectedResponse);

    ProfileResponse result =
      profileService.updateProfile(1L, request);

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

    verify(userProfileRepository).save(profile);
    verify(profileMapper).toResponse(user, profile);

    assertThat(result).isEqualTo(expectedResponse);
  }

  @Test
  void updateProfile_UserDoesNotExist_ThrowsUserNotFoundException() {

    UpdateProfileRequest request = new UpdateProfileRequest(
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
      () -> profileService.updateProfile(1L, request)
    )
      .isInstanceOf(UserNotFoundException.class)
      .hasMessage("User not found with ID: 1");

    verify(userRepository).findById(1L);
    verifyNoInteractions(userProfileRepository, profileMapper);
  }

  @Test
  void updateProfile_ProfileDoesNotExist_ThrowsProfileNotFoundException() {

    UpdateProfileRequest request = new UpdateProfileRequest(
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
      () -> profileService.updateProfile(1L, request)
    )
      .isInstanceOf(ProfileNotFoundException.class)
      .hasMessage("Profile not found for user with ID: 1");

    verify(userRepository).findById(1L);
    verify(userProfileRepository).findByUserId(1L);
    verifyNoInteractions(profileMapper);
  }

  @Test
  void createProfile_ValidUserId_SavesProfile() {

    profileService.createProfile(1L);

    ArgumentCaptor<UserProfile> captor =
      ArgumentCaptor.forClass(UserProfile.class);

    verify(userProfileRepository).save(captor.capture());

    UserProfile savedProfile = captor.getValue();

    assertThat(savedProfile.getUserId()).isEqualTo(1L);
    assertThat(savedProfile.getPhone()).isNull();
    assertThat(savedProfile.getCountry()).isNull();
    assertThat(savedProfile.getTimezone()).isNull();
    assertThat(savedProfile.getDepartment()).isNull();
    assertThat(savedProfile.getOffice()).isNull();
    assertThat(savedProfile.getBio()).isNull();
  }
}
