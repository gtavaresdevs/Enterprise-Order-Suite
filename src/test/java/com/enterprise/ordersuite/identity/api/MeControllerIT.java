package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import com.enterprise.ordersuite.storage.ObjectStorageProperties;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Own account (Tenancy & Identity, Own account 1; D-18 folds the profile into /me). Every
// role may call it (§5.3); the denied path is an anonymous caller.
@IntegrationTest
@AutoConfigureMockMvc
class MeControllerIT {

  private static final String PASSWORD = "Password123!";

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private MembershipRepository membershipRepository;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private S3Client s3Client;

  @Autowired
  private ObjectStorageProperties storageProperties;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  // -------- GET /me --------

  @Test
  void me_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void me_asOwner_returnsTheUserMembershipAndRestaurant_andNoSecrets() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    var membership = membershipRepository.findByUserId(user.getId()).orElseThrow();
    var restaurant = restaurantRepository.findById(membership.getRestaurantId()).orElseThrow();

    me(login(email))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.firstName").value("Test"))
      .andExpect(jsonPath("$.lastName").value("User"))
      .andExpect(jsonPath("$.phone").isEmpty())
      .andExpect(jsonPath("$.avatarUrl").isEmpty())
      .andExpect(jsonPath("$.platformAdmin").value(false))
      .andExpect(jsonPath("$.membership.restaurantId").value(restaurant.getId()))
      .andExpect(jsonPath("$.membership.role").value("OWNER"))
      .andExpect(jsonPath("$.membership.memberSince").isNotEmpty())
      .andExpect(jsonPath("$.restaurant.id").value(restaurant.getId()))
      .andExpect(jsonPath("$.restaurant.name").value(restaurant.getName()))
      .andExpect(jsonPath("$.restaurant.slug").value(restaurant.getSlug()))
      .andExpect(jsonPath("$.restaurant.storefrontUrl").value(endsWith("/r/" + restaurant.getSlug())))
      .andExpect(jsonPath("$.restaurant.timezone").value("America/Sao_Paulo"))
      .andExpect(jsonPath("$.restaurant.currency").value("BRL"))
      .andExpect(jsonPath("$.password").doesNotExist())
      .andExpect(jsonPath("$.active").doesNotExist())
      .andExpect(jsonPath("$.avatarKey").doesNotExist());
  }

  @Test
  void me_asStaff_returnsTheStaffRole() throws Exception {
    String email = uniqueEmail();
    testUsers.member(email, PASSWORD, MembershipRole.STAFF);

    me(login(email))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.membership.role").value("STAFF"));
  }

  @Test
  void me_asPlatformAdmin_hasNoMembershipOrRestaurant() throws Exception {
    String email = uniqueEmail();
    testUsers.platformAdmin(email, PASSWORD);

    me(login(email))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.platformAdmin").value(true))
      .andExpect(jsonPath("$.membership").isEmpty())
      .andExpect(jsonPath("$.restaurant").isEmpty());
  }

  // -------- PATCH /me --------

  @Test
  void patchMe_withoutToken_returns401() throws Exception {
    mockMvc.perform(patch("/me")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"firstName\":\"X\"}"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void patchMe_updatesNamesAndPhone_andKeepsTheEmail() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.member(email, PASSWORD, MembershipRole.MANAGER);
    String token = login(email);

    patchMe(token, "{\"firstName\":\"Ana\",\"lastName\":\"Souza\",\"phone\":\"+5511999998888\"}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Ana"))
      .andExpect(jsonPath("$.lastName").value("Souza"))
      .andExpect(jsonPath("$.phone").value("+5511999998888"))
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.membership.role").value("MANAGER"));

    User saved = userRepository.findById(user.getId()).orElseThrow();
    assertThat(saved.getFirstName()).isEqualTo("Ana");
    assertThat(saved.getPhone()).isEqualTo("+5511999998888");
    assertThat(saved.getEmail()).as("email is read-only (Own account 1)").isEqualTo(email);
  }

  @Test
  void patchMe_withAnEmail_isRejectedAsAnUnknownField_andTheEmailStays() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String token = login(email);

    patchMe(token, "{\"firstName\":\"Ana\",\"email\":\"other@test.com\"}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
      .andExpect(jsonPath("$.errors[0]").value("email: unknown field"));

    User saved = userRepository.findById(user.getId()).orElseThrow();
    assertThat(saved.getEmail()).as("email is read-only (Own account 1)").isEqualTo(email);
    assertThat(saved.getFirstName()).as("a refused request changes nothing").isEqualTo("Test");
  }

  @Test
  void patchMe_absentFieldsStayAsTheyAre_andAnExplicitNullPhoneRemovesIt() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String token = login(email);
    patchMe(token, "{\"phone\":\"+5511999998888\"}").andExpect(status().isOk());

    patchMe(token, "{\"firstName\":\"Only\"}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Only"))
      .andExpect(jsonPath("$.lastName").value("User"))
      .andExpect(jsonPath("$.phone").value("+5511999998888"));

    patchMe(token, "{\"phone\":null}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.phone").isEmpty());
    assertThat(userRepository.findById(user.getId()).orElseThrow().getPhone()).isNull();
  }

  @Test
  void patchMe_blankName_returns400() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);

    patchMe(login(email), "{\"firstName\":\" \"}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void patchMe_phoneNotInE164_returns400_andChangesNothing() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);

    patchMe(login(email), "{\"firstName\":\"Changed\",\"phone\":\"11 99999-8888\"}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getFirstName()).isEqualTo("Test");
  }

  // -------- /me/avatar --------

  @Test
  void uploadAvatar_withoutToken_returns401() throws Exception {
    mockMvc.perform(multipart("/me/avatar").file(png()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void uploadAvatar_asMember_storesWebpUnderTheRestaurantPrefix() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String restaurantId = membershipRepository.findByUserId(user.getId()).orElseThrow().getRestaurantId();

    uploadAvatar(login(email), png())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(user.getId()))
      .andExpect(jsonPath("$.avatarUrl").value(endsWith(".webp")));

    String key = userRepository.findById(user.getId()).orElseThrow().getAvatarKey();
    assertThat(key)
      .as("restaurant-scoped storage key (Tenancy & Identity §5.2)")
      .startsWith("restaurants/" + restaurantId + "/users/" + user.getId() + "/")
      .endsWith(".webp");
    assertThat(exists(key)).isTrue();
  }

  @Test
  void uploadAvatar_asPlatformAdmin_storesUnderTheUserPrefix() throws Exception {
    String email = uniqueEmail();
    User admin = testUsers.platformAdmin(email, PASSWORD);

    uploadAvatar(login(email), png()).andExpect(status().isOk());

    String key = userRepository.findById(admin.getId()).orElseThrow().getAvatarKey();
    assertThat(key).as("the platform admin has no restaurant").startsWith("users/" + admin.getId() + "/");
    assertThat(exists(key)).isTrue();
  }

  @Test
  void uploadAvatar_again_replacesTheObject_andDeletesTheOldOne() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String token = login(email);
    uploadAvatar(token, png()).andExpect(status().isOk());
    String first = userRepository.findById(user.getId()).orElseThrow().getAvatarKey();

    uploadAvatar(token, png()).andExpect(status().isOk());

    String second = userRepository.findById(user.getId()).orElseThrow().getAvatarKey();
    assertThat(second).isNotEqualTo(first);
    assertThat(exists(second)).isTrue();
    assertThat(exists(first)).as("the replaced avatar is deleted after commit").isFalse();
  }

  @Test
  void uploadAvatar_notAnImage_returns400_andKeepsTheCurrentAvatar() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String token = login(email);
    uploadAvatar(token, png()).andExpect(status().isOk());
    String current = userRepository.findById(user.getId()).orElseThrow().getAvatarKey();

    MockMultipartFile text = new MockMultipartFile(
      "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "not an image".getBytes(StandardCharsets.UTF_8));
    uploadAvatar(token, text)
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_AVATAR"));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getAvatarKey()).isEqualTo(current);
    assertThat(exists(current)).isTrue();
  }

  @Test
  void deleteAvatar_removesTheKeyAndTheObject() throws Exception {
    String email = uniqueEmail();
    User user = testUsers.owner(email, PASSWORD);
    String token = login(email);
    uploadAvatar(token, png()).andExpect(status().isOk());
    String key = userRepository.findById(user.getId()).orElseThrow().getAvatarKey();

    mockMvc.perform(delete("/me/avatar").header("Authorization", "Bearer " + token))
      .andExpect(status().isNoContent());

    assertThat(userRepository.findById(user.getId()).orElseThrow().getAvatarKey()).isNull();
    assertThat(exists(key)).isFalse();
    me(token).andExpect(jsonPath("$.avatarUrl").isEmpty());
  }

  @Test
  void deleteAvatar_withoutAnAvatar_returns204() throws Exception {
    String email = uniqueEmail();
    testUsers.owner(email, PASSWORD);

    mockMvc.perform(delete("/me/avatar").header("Authorization", "Bearer " + login(email)))
      .andExpect(status().isNoContent());
  }

  @Test
  void deleteAvatar_withoutToken_returns401() throws Exception {
    mockMvc.perform(delete("/me/avatar")).andExpect(status().isUnauthorized());
  }

  private ResultActions me(String token) throws Exception {
    return mockMvc.perform(get("/me").header("Authorization", "Bearer " + token));
  }

  private ResultActions patchMe(String token, String json) throws Exception {
    return mockMvc.perform(patch("/me")
      .header("Authorization", "Bearer " + token)
      .contentType(MediaType.APPLICATION_JSON)
      .content(json));
  }

  private ResultActions uploadAvatar(String token, MockMultipartFile file) throws Exception {
    return mockMvc.perform(multipart("/me/avatar").file(file).header("Authorization", "Bearer " + token));
  }

  private boolean exists(String key) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(storageProperties.bucket()).key(key).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  private static MockMultipartFile png() throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, out.toByteArray());
  }

  private String login(String email) throws Exception {
    String body = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("accessToken").asText();
  }

  private static String uniqueEmail() {
    return "me-" + UUID.randomUUID() + "@test.com";
  }
}
