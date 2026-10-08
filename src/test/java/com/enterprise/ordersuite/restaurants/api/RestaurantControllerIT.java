package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import com.enterprise.ordersuite.security.web.TenantContextFilter;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET/PATCH /restaurant (Tenancy & Identity §5.3, §5.6). The restaurant always comes from the
// caller's token, so a restaurant path has no id to cross tenants with: the cross-tenant
// cases check that each owner reads and edits only their own.
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.urls.public-base=https://order.example.com/")
class RestaurantControllerIT {

  private static final String PASSWORD = "Password123!";
  private static final String UNKNOWN_RESTAURANT = "01J00000000000000000000000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private IdentityAuditEventRepository auditEventRepository;

  @Test
  void get_asEachMemberRole_returnsTheirRestaurant() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);
    String slug = restaurantRepository.findById(restaurantId).orElseThrow().getSlug();
    User manager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    User staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    for (User member : List.of(owner, manager, staff)) {
      mockMvc.perform(get("/restaurant").header("Authorization", bearer(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(restaurantId))
        .andExpect(jsonPath("$.name").value("Test Restaurant"))
        .andExpect(jsonPath("$.slug").value(slug))
        .andExpect(jsonPath("$.storefrontUrl").value("https://order.example.com/r/" + slug))
        .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
        .andExpect(jsonPath("$.currency").value("BRL"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }
  }

  @Test
  void get_ownersOfTwoRestaurants_eachSeeOnlyTheirOwn() throws Exception {
    User ownerR1 = testUsers.owner(uniqueEmail(), PASSWORD);
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/restaurant").header("Authorization", bearer(ownerR2)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(testUsers.restaurantOf(ownerR2)));
    assertThat(testUsers.restaurantOf(ownerR2)).isNotEqualTo(testUsers.restaurantOf(ownerR1));
  }

  @Test
  void get_platformAdminWithoutSupportHeader_isForbidden() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/restaurant").header("Authorization", bearer(admin)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void get_platformAdminSupportRead_returnsThatRestaurant() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(get("/restaurant")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(restaurantId));
  }

  @Test
  void get_platformAdminSupportReadOfAnUnknownRestaurant_is404() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/restaurant")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, UNKNOWN_RESTAURANT))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
  }

  @Test
  void get_memberWithSupportHeader_isForbidden() throws Exception {
    User staff = testUsers.member(uniqueEmail(), PASSWORD, MembershipRole.STAFF);
    String otherRestaurant = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(get("/restaurant")
        .header("Authorization", bearer(staff))
        .header(TenantContextFilter.SUPPORT_HEADER, otherRestaurant))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void get_anonymous_is401() throws Exception {
    mockMvc.perform(get("/restaurant"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void patch_asOwner_renamesTrimmed_andAudits() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);

    mockMvc.perform(patchName(owner, "  Cantina Nova  "))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(restaurantId))
      .andExpect(jsonPath("$.name").value("Cantina Nova"));

    assertThat(restaurantRepository.findById(restaurantId).orElseThrow().getName()).isEqualTo("Cantina Nova");
    List<IdentityAuditEvent> events = eventsOf(restaurantId);
    assertThat(events).hasSize(1);
    IdentityAuditEvent event = events.get(0);
    assertThat(event.getType()).isEqualTo(IdentityAuditEventType.RESTAURANT_UPDATED);
    assertThat(event.getActorUserId()).isEqualTo(owner.getId());
    assertThat(event.getTargetUserId()).isNull();
    assertThat(event.getDetails()).isEqualTo(Map.of("name", Map.of("from", "Test Restaurant", "to", "Cantina Nova")));
  }

  @Test
  void patch_sameName_changesNothing_andWritesNoAuditEvent() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(patchName(owner, "Test Restaurant"))
      .andExpect(status().isOk());

    assertThat(eventsOf(testUsers.restaurantOf(owner))).isEmpty();
  }

  @Test
  void patch_ownerOfR2_changesOnlyR2() throws Exception {
    User ownerR1 = testUsers.owner(uniqueEmail(), PASSWORD);
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(patchName(ownerR2, "Only R2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(testUsers.restaurantOf(ownerR2)));

    assertThat(restaurantRepository.findById(testUsers.restaurantOf(ownerR1)).orElseThrow().getName())
      .isEqualTo("Test Restaurant");
  }

  @Test
  void patch_asManagerOrStaff_isForbidden() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);
    User manager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    User staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    for (User member : List.of(manager, staff)) {
      mockMvc.perform(patchName(member, "Renamed"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertThat(restaurantRepository.findById(restaurantId).orElseThrow().getName()).isEqualTo("Test Restaurant");
  }

  @Test
  void patch_platformAdmin_isForbidden_withOrWithoutTheSupportHeader() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(patchName(admin, "Renamed"))
      .andExpect(status().isForbidden());
    mockMvc.perform(patchName(admin, "Renamed").header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void patch_blankName_is400() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(patchName(owner, "   "))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void patch_unknownField_isRejected_soARestaurantIdInTheBodyCannotRedirectIt() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String otherRestaurant = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(patch("/restaurant")
        .header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"X\",\"restaurantId\":\"" + otherRestaurant + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
      .andExpect(jsonPath("$.errors[0]").value("restaurantId: unknown field"));
  }

  private MockHttpServletRequestBuilder patchName(User user, String name) throws Exception {
    return patch("/restaurant")
      .header("Authorization", bearer(user))
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(Map.of("name", name)));
  }

  private List<IdentityAuditEvent> eventsOf(String restaurantId) {
    return auditEventRepository.findAll().stream()
      .filter(event -> restaurantId.equals(event.getRestaurantId()))
      .toList();
  }

  private String bearer(User user) throws Exception {
    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(user.getEmail(), PASSWORD))))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
  }

  private static String uniqueEmail() {
    return "restaurant-" + UUID.randomUUID() + "@test.com";
  }
}
