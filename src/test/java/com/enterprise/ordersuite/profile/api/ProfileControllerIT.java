package com.enterprise.ordersuite.profile.api;

import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.profile.api.dto.UpdateProfileRequest;
import com.enterprise.ordersuite.profile.domain.UserProfile;
import com.enterprise.ordersuite.profile.persistence.UserProfileRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import com.enterprise.ordersuite.storage.ObjectStorageService;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ProfileControllerIT {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private UserProfileRepository userProfileRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private ObjectStorageService objectStorageService;

  private Role userRole;

  @BeforeEach
  void setup() {
    userRole = roleRepository.findByName("USER").orElseThrow();
  }

  @Test
  void getProfile_AuthenticatedUser_ReturnsProfile() throws Exception {

    User user = createUser(
      "profile-get-" + UUID.randomUUID() + "@test.com",
      "John",
      "Doe"
    );

    UserProfile profile = new UserProfile(user.getId());
    profile.setPhone("+1 555 123 4567");
    profile.setCountry("United States");
    profile.setTimezone("America/New_York");
    profile.setDepartment("Operations");
    profile.setOffice("New York");
    profile.setBio("Operations manager");

    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    mockMvc.perform(
        get("/me/profile")
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.email").value(user.getEmail()))
      .andExpect(jsonPath("$.firstName").value("John"))
      .andExpect(jsonPath("$.lastName").value("Doe"))
      .andExpect(jsonPath("$.role").value("USER"))
      .andExpect(jsonPath("$.phone").value("+1 555 123 4567"))
      .andExpect(jsonPath("$.country").value("United States"))
      .andExpect(jsonPath("$.timezone").value("America/New_York"))
      .andExpect(jsonPath("$.department").value("Operations"))
      .andExpect(jsonPath("$.office").value("New York"))
      .andExpect(jsonPath("$.bio").value("Operations manager"))
      .andExpect(jsonPath("$.avatarUrl").isEmpty());
  }

  @Test
  void getProfile_AuthenticatedUser_WithAvatar_ReturnsAvatarUrl()
    throws Exception {

    User user = createUser(
      "profile-avatar-get-" + UUID.randomUUID() + "@test.com",
      "John",
      "Doe"
    );

    UserProfile profile = new UserProfile(user.getId());
    profile.setAvatarKey("avatars/existing-avatar.webp");

    userProfileRepository.save(profile);

    String expectedUrl =
      "http://localhost:9000/eos-storage/avatars/existing-avatar.webp";

    when(objectStorageService.getUrl("avatars/existing-avatar.webp"))
      .thenReturn(expectedUrl);

    String token = jwtService.generateToken(user);

    mockMvc.perform(
        get("/me/profile")
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.avatarUrl").value(expectedUrl));

    verify(objectStorageService)
      .getUrl("avatars/existing-avatar.webp");
  }

  @Test
  void getProfile_UnauthenticatedRequest_Returns401() throws Exception {

    mockMvc.perform(
        get("/me/profile")
          .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void getProfile_AuthenticatedUser_ReturnsOnlyAuthenticatedUsersProfile()
    throws Exception {

    User firstUser = createUser(
      "profile-first-" + UUID.randomUUID() + "@test.com",
      "First",
      "User"
    );

    User secondUser = createUser(
      "profile-second-" + UUID.randomUUID() + "@test.com",
      "Second",
      "User"
    );

    UserProfile firstProfile = new UserProfile(firstUser.getId());
    firstProfile.setCountry("Brazil");
    userProfileRepository.save(firstProfile);

    UserProfile secondProfile = new UserProfile(secondUser.getId());
    secondProfile.setCountry("United States");
    userProfileRepository.save(secondProfile);

    String token = jwtService.generateToken(firstUser);

    mockMvc.perform(
        get("/me/profile")
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(firstUser.getId()))
      .andExpect(jsonPath("$.email").value(firstUser.getEmail()))
      .andExpect(jsonPath("$.firstName").value("First"))
      .andExpect(jsonPath("$.country").value("Brazil"))
      .andExpect(jsonPath("$.id").value(firstUser.getId()))
      .andExpect(jsonPath("$.email").value(firstUser.getEmail()));
  }

  @Test
  void updateProfile_AuthenticatedUser_UpdatesProfile() throws Exception {

    User user = createUser(
      "profile-update-" + UUID.randomUUID() + "@test.com",
      "Update",
      "User"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    UpdateProfileRequest request = new UpdateProfileRequest(
      "+55 62 99999-9999",
      "Brazil",
      "America/Sao_Paulo",
      "Engineering",
      "Goiania",
      "Senior backend engineer"
    );

    mockMvc.perform(
        patch("/me/profile")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(request))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.email").value(user.getEmail()))
      .andExpect(jsonPath("$.firstName").value("Update"))
      .andExpect(jsonPath("$.lastName").value("User"))
      .andExpect(jsonPath("$.phone").value("+55 62 99999-9999"))
      .andExpect(jsonPath("$.country").value("Brazil"))
      .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
      .andExpect(jsonPath("$.department").value("Engineering"))
      .andExpect(jsonPath("$.office").value("Goiania"))
      .andExpect(jsonPath("$.bio").value("Senior backend engineer"))
      .andExpect(jsonPath("$.avatarUrl").isEmpty());

    UserProfile updatedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(updatedProfile.getPhone())
      .isEqualTo("+55 62 99999-9999");

    assertThat(updatedProfile.getCountry())
      .isEqualTo("Brazil");

    assertThat(updatedProfile.getTimezone())
      .isEqualTo("America/Sao_Paulo");

    assertThat(updatedProfile.getDepartment())
      .isEqualTo("Engineering");

    assertThat(updatedProfile.getOffice())
      .isEqualTo("Goiania");

    assertThat(updatedProfile.getBio())
      .isEqualTo("Senior backend engineer");
  }

  @Test
  void updateProfile_UnauthenticatedRequest_Returns401() throws Exception {

    UpdateProfileRequest request = new UpdateProfileRequest(
      "123",
      "Brazil",
      "America/Sao_Paulo",
      "Engineering",
      "Goiania",
      "Bio"
    );

    mockMvc.perform(
        patch("/me/profile")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(request))
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void updateProfile_CannotModifyIdentityFields() throws Exception {

    User user = createUser(
      "profile-security-" + UUID.randomUUID() + "@test.com",
      "Original",
      "Name"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    String payload = """
      {
        "firstName": "Hacker",
        "lastName": "User",
        "email": "hacker@test.com",
        "role": "ADMIN",
        "phone": "+55 62 99999-9999",
        "country": "Brazil",
        "timezone": "America/Sao_Paulo",
        "department": "Engineering",
        "office": "Goiania",
        "bio": "Updated bio"
      }
      """;

    mockMvc.perform(
        patch("/me/profile")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content(payload)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Original"))
      .andExpect(jsonPath("$.lastName").value("Name"))
      .andExpect(jsonPath("$.email").value(user.getEmail()))
      .andExpect(jsonPath("$.role").value("USER"))
      .andExpect(jsonPath("$.phone").value("+55 62 99999-9999"));

    User unchangedUser =
      userRepository.findById(user.getId()).orElseThrow();

    assertThat(unchangedUser.getFirstName())
      .isEqualTo("Original");

    assertThat(unchangedUser.getLastName())
      .isEqualTo("Name");

    assertThat(unchangedUser.getEmail())
      .isEqualTo(user.getEmail());

    assertThat(unchangedUser.getRole().getName())
      .isEqualTo("USER");
  }

  @Test
  void uploadAvatar_AuthenticatedUser_UploadsAvatar()
    throws Exception {

    User user = createUser(
      "avatar-upload-" + UUID.randomUUID() + "@test.com",
      "Avatar",
      "User"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    byte[] image = createPngImage();

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "avatar.png",
      MediaType.IMAGE_PNG_VALUE,
      image
    );

    when(objectStorageService.upload(
      any(String.class),
      any(),
      anyLong(),
      eq("image/webp")
    )).thenAnswer(invocation ->
      invocation.getArgument(0)
    );

    when(objectStorageService.getUrl(any(String.class)))
      .thenAnswer(invocation ->
        "http://localhost:9000/eos-storage/"
          + invocation.getArgument(0)
      );

    mockMvc.perform(
        multipart("/me/profile/avatar")
          .file(file)
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.email").value(user.getEmail()))
      .andExpect(jsonPath("$.avatarUrl").value(
        org.hamcrest.Matchers.startsWith(
          "http://localhost:9000/eos-storage/avatars/"
        )
      ));

    UserProfile updatedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(updatedProfile.getAvatarKey())
      .isNotNull()
      .startsWith("avatars/")
      .endsWith(".webp");

    verify(objectStorageService).upload(
      eq(updatedProfile.getAvatarKey()),
      any(),
      anyLong(),
      eq("image/webp")
    );

    verify(objectStorageService)
      .getUrl(updatedProfile.getAvatarKey());

    verify(objectStorageService, never())
      .delete(any(String.class));
  }

  @Test
  void uploadAvatar_AuthenticatedUser_ReplacesExistingAvatar()
    throws Exception {

    User user = createUser(
      "avatar-replace-" + UUID.randomUUID() + "@test.com",
      "Avatar",
      "Replace"
    );

    UserProfile profile = new UserProfile(user.getId());

    String oldAvatarKey =
      "avatars/old-avatar.webp";

    profile.setAvatarKey(oldAvatarKey);

    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    byte[] image = createPngImage();

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "new-avatar.png",
      MediaType.IMAGE_PNG_VALUE,
      image
    );

    when(objectStorageService.upload(
      any(String.class),
      any(),
      anyLong(),
      eq("image/webp")
    )).thenAnswer(invocation ->
      invocation.getArgument(0)
    );

    when(objectStorageService.getUrl(any(String.class)))
      .thenAnswer(invocation ->
        "http://localhost:9000/eos-storage/"
          + invocation.getArgument(0)
      );

    mockMvc.perform(
        multipart("/me/profile/avatar")
          .file(file)
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isOk());

    UserProfile updatedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(updatedProfile.getAvatarKey())
      .isNotNull()
      .isNotEqualTo(oldAvatarKey);

    verify(objectStorageService)
      .delete(oldAvatarKey);

    verify(objectStorageService)
      .upload(
        eq(updatedProfile.getAvatarKey()),
        any(),
        anyLong(),
        eq("image/webp")
      );
  }

  @Test
  void uploadAvatar_UnauthenticatedRequest_Returns401()
    throws Exception {

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "avatar.png",
      MediaType.IMAGE_PNG_VALUE,
      createPngImage()
    );

    mockMvc.perform(
        multipart("/me/profile/avatar")
          .file(file)
      )
      .andExpect(status().isUnauthorized());

    verifyNoStorageInteractions();
  }

  @Test
  void uploadAvatar_MissingFile_Returns400()
    throws Exception {

    User user = createUser(
      "avatar-missing-" + UUID.randomUUID() + "@test.com",
      "Missing",
      "Avatar"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    mockMvc.perform(
        multipart("/me/profile/avatar")
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isBadRequest());

    UserProfile unchangedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(unchangedProfile.getAvatarKey())
      .isNull();

    verifyNoStorageInteractions();
  }

  @Test
  void uploadAvatar_InvalidFileType_Returns400()
    throws Exception {

    User user = createUser(
      "avatar-invalid-" + UUID.randomUUID() + "@test.com",
      "Invalid",
      "Avatar"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "avatar.txt",
      MediaType.TEXT_PLAIN_VALUE,
      "not an image".getBytes(StandardCharsets.UTF_8)
    );

    mockMvc.perform(
        multipart("/me/profile/avatar")
          .file(file)
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isBadRequest());

    UserProfile unchangedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(unchangedProfile.getAvatarKey())
      .isNull();

    verifyNoStorageInteractions();
  }

  @Test
  void deleteAvatar_AuthenticatedUser_DeletesExistingAvatar()
    throws Exception {

    User user = createUser(
      "avatar-delete-" + UUID.randomUUID() + "@test.com",
      "Delete",
      "Avatar"
    );

    UserProfile profile = new UserProfile(user.getId());

    String avatarKey =
      "avatars/avatar-to-delete.webp";

    profile.setAvatarKey(avatarKey);

    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    when(objectStorageService.getUrl(any(String.class)))
      .thenReturn(null);

    mockMvc.perform(
        delete("/me/profile/avatar")
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.avatarUrl").isEmpty());

    UserProfile updatedProfile =
      userProfileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(updatedProfile.getAvatarKey())
      .isNull();

    verify(objectStorageService)
      .delete(avatarKey);

    verify(objectStorageService, never())
      .getUrl(avatarKey);
  }

  @Test
  void deleteAvatar_AuthenticatedUser_WithoutAvatar_DoesNothing()
    throws Exception {

    User user = createUser(
      "avatar-delete-none-" + UUID.randomUUID() + "@test.com",
      "No",
      "Avatar"
    );

    UserProfile profile = new UserProfile(user.getId());
    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    mockMvc.perform(
        delete("/me/profile/avatar")
          .header("Authorization", "Bearer " + token)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.avatarUrl").isEmpty());

    verify(objectStorageService, never())
      .delete(any(String.class));

    verify(objectStorageService, never())
      .getUrl(any(String.class));
  }

  @Test
  void deleteAvatar_UnauthenticatedRequest_Returns401()
    throws Exception {

    mockMvc.perform(
        delete("/me/profile/avatar")
      )
      .andExpect(status().isUnauthorized());

    verifyNoStorageInteractions();
  }

  private void verifyNoStorageInteractions() {
    org.mockito.Mockito.verifyNoInteractions(objectStorageService);
  }

  private byte[] createPngImage() throws Exception {
    java.awt.image.BufferedImage image =
      new java.awt.image.BufferedImage(
        2,
        2,
        java.awt.image.BufferedImage.TYPE_INT_RGB
      );

    java.awt.Graphics2D graphics = image.createGraphics();

    try {
      graphics.fillRect(0, 0, 2, 2);
    } finally {
      graphics.dispose();
    }

    java.io.ByteArrayOutputStream outputStream =
      new java.io.ByteArrayOutputStream();

    javax.imageio.ImageIO.write(
      image,
      "png",
      outputStream
    );

    return outputStream.toByteArray();
  }

  private User createUser(
    String email,
    String firstName,
    String lastName
  ) {
    User user = new User();

    user.setEmail(email);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword(
      passwordEncoder.encode("TestPassword123!@#")
    );
    user.setRole(userRole);
    user.setActive(true);

    return userRepository.save(user);
  }
}
