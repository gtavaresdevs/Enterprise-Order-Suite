package com.enterprise.ordersuite.profile.api;

import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
import com.enterprise.ordersuite.profile.domain.UserProfile;
import com.enterprise.ordersuite.profile.persistence.UserProfileRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import com.enterprise.ordersuite.profile.api.dto.UpdateProfileRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
  private OrderRepository orderRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtService jwtService;

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
        get("/api/v1/me/profile")
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
      .andExpect(jsonPath("$.bio").value("Operations manager"));
  }

  @Test
  void getProfile_UnauthenticatedRequest_Returns401() throws Exception {

    mockMvc.perform(
        get("/api/v1/me/profile")
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

    UserProfile firstProfile =
      new UserProfile(firstUser.getId());

    firstProfile.setCountry("Brazil");

    userProfileRepository.save(firstProfile);

    UserProfile secondProfile =
      new UserProfile(secondUser.getId());

    secondProfile.setCountry("United States");

    userProfileRepository.save(secondProfile);

    String token = jwtService.generateToken(firstUser);

    String response =
      mockMvc.perform(
          get("/api/v1/me/profile")
            .header(
              "Authorization",
              "Bearer " + token
            )
            .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstUser.getId()))
        .andExpect(jsonPath("$.email").value(firstUser.getEmail()))
        .andExpect(jsonPath("$.firstName").value("First"))
        .andExpect(jsonPath("$.country").value("Brazil"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode json = objectMapper.readTree(response);

    assertThat(json.get("id").asLong())
      .isNotEqualTo(secondUser.getId());

    assertThat(json.get("email").asText())
      .isNotEqualTo(secondUser.getEmail());
  }

  @Test
  void updateProfile_AuthenticatedUser_UpdatesProfile() throws Exception {

    User user = createUser(
      "profile-update-" + UUID.randomUUID() + "@test.com",
      "Update",
      "User"
    );

    UserProfile profile =
      new UserProfile(user.getId());

    userProfileRepository.save(profile);

    String token = jwtService.generateToken(user);

    UpdateProfileRequest request =
      new UpdateProfileRequest(
        "+55 62 99999-9999",
        "Brazil",
        "America/Sao_Paulo",
        "Engineering",
        "Goiania",
        "Senior backend engineer"
      );

    mockMvc.perform(
        patch("/api/v1/me/profile")
          .header(
            "Authorization",
            "Bearer " + token
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(request)
          )
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
      .andExpect(jsonPath("$.bio").value("Senior backend engineer"));

    UserProfile updatedProfile =
      userProfileRepository
        .findByUserId(user.getId())
        .orElseThrow();

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

    UpdateProfileRequest request =
      new UpdateProfileRequest(
        "123",
        "Brazil",
        "America/Sao_Paulo",
        "Engineering",
        "Goiania",
        "Bio"
      );

    mockMvc.perform(
        patch("/api/v1/me/profile")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            objectMapper.writeValueAsString(request)
          )
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

    UserProfile profile =
      new UserProfile(user.getId());

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
        patch("/api/v1/me/profile")
          .header(
            "Authorization",
            "Bearer " + token
          )
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
      userRepository.findById(user.getId())
        .orElseThrow();

    assertThat(unchangedUser.getFirstName())
      .isEqualTo("Original");

    assertThat(unchangedUser.getLastName())
      .isEqualTo("Name");

    assertThat(unchangedUser.getEmail())
      .isEqualTo(user.getEmail());

    assertThat(unchangedUser.getRole().getName())
      .isEqualTo("USER");
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
