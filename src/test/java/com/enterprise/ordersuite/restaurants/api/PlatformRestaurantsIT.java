package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import com.enterprise.ordersuite.security.web.TenantContextFilter;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestEmailServiceConfig;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /platform/restaurants* (Tenancy & Identity, Restaurant creation, §5.3, D-4, D-6, D-14;
// acceptance 2). The platform admin only; every member role is 403 on every operation.
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestEmailServiceConfig.class)
@TestPropertySource(properties = "app.urls.public-base=https://order.example.com")
class PlatformRestaurantsIT {

  private static final String PASSWORD = "Password123!";
  private static final String UNKNOWN_RESTAURANT = "01J00000000000000000000000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private MembershipRepository membershipRepository;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private IdentityAuditEventRepository auditEventRepository;

  @Autowired
  private TestEmailServiceConfig.CapturingEmailService emails;

  private User admin;

  @BeforeEach
  void platformAdmin() {
    emails.clear();
    admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
  }

  // ---- create ----

  @Test
  void create_asPlatformAdmin_createsTheRestaurantAndInvitesItsOwner() throws Exception {
    String slug = uniqueSlug();
    String ownerEmail = uniqueEmail();

    String body = create(admin, createBody("Cantina Nonna", slug, null, ownerEmail))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isNotEmpty())
      .andExpect(jsonPath("$.name").value("Cantina Nonna"))
      .andExpect(jsonPath("$.slug").value(slug))
      .andExpect(jsonPath("$.storefrontUrl").value("https://order.example.com/r/" + slug))
      .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
      .andExpect(jsonPath("$.currency").value("BRL"))
      .andExpect(jsonPath("$.createdAt").isNotEmpty())
      .andReturn().getResponse().getContentAsString();
    String restaurantId = objectMapper.readTree(body).get("id").asText();

    User owner = userRepository.findByEmailIgnoreCase(ownerEmail).orElseThrow();
    assertThat(owner.getPassword()).as("an invited owner has no password yet").isNull();
    assertThat(owner.getFirstName()).isEqualTo("Ana");
    assertThat(owner.isPlatformAdmin()).isFalse();
    Membership membership = membershipRepository.findByUserId(owner.getId()).orElseThrow();
    assertThat(membership.getRestaurantId()).isEqualTo(restaurantId);
    assertThat(membership.getRole()).isEqualTo(MembershipRole.OWNER);

    assertThat(emails.invitations()).singleElement()
      .satisfies(email -> assertThat(email.toEmail()).isEqualTo(ownerEmail));

    IdentityAuditEvent created = singleEvent(IdentityAuditEventType.RESTAURANT_CREATED, restaurantId);
    assertThat(created.getRestaurantId()).as("restaurant creation is a platform-level event").isNull();
    assertThat(created.getActorUserId()).isEqualTo(admin.getId());
    assertThat(created.getDetails()).containsEntry("slug", slug).containsEntry("name", "Cantina Nonna")
      .containsEntry("timezone", "America/Sao_Paulo");
    assertThat(eventsOf(restaurantId)).singleElement().satisfies(invited -> {
      assertThat(invited.getType()).isEqualTo(IdentityAuditEventType.MEMBER_INVITED);
      assertThat(invited.getActorUserId()).isEqualTo(admin.getId());
      assertThat(invited.getTargetUserId()).isEqualTo(owner.getId());
      assertThat(invited.getDetails()).isEqualTo(Map.of("role", "OWNER"));
    });
  }

  // Acceptance 2: the owner sets a password from the email and lands in their restaurant.
  @Test
  void create_thenTheOwnerSetsAPassword_andMeShowsTheRestaurantAsOwner() throws Exception {
    String ownerEmail = uniqueEmail();
    String restaurantId = createdId(create(admin, createBody("R1", uniqueSlug(), null, ownerEmail)));

    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
          "token", tokenOf(emails.invitations().get(0).resetUrl()), "newPassword", "Brand-new-pass1"))))
      .andExpect(status().is2xxSuccessful());

    String ownerToken = bearer(ownerEmail, "Brand-new-pass1");
    mockMvc.perform(get("/me").header("Authorization", ownerToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.membership.restaurantId").value(restaurantId))
      .andExpect(jsonPath("$.membership.role").value("OWNER"))
      .andExpect(jsonPath("$.restaurant.name").value("R1"));
  }

  @Test
  void create_withATimezone_usesIt() throws Exception {
    create(admin, createBody("Lisboa", uniqueSlug(), "Europe/Lisbon", uniqueEmail()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
  }

  @Test
  void create_withABlankTimezone_usesTheDefault() throws Exception {
    create(admin, createBody("Blank", uniqueSlug(), "  ", uniqueEmail()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));
  }

  @Test
  void create_takenSlug_isSlugTaken_andCreatesNothing() throws Exception {
    String slug = uniqueSlug();
    create(admin, createBody("First", slug, null, uniqueEmail())).andExpect(status().isCreated());
    emails.clear();
    String secondOwner = uniqueEmail();

    create(admin, createBody("Second", slug, null, secondOwner))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

    assertThat(userRepository.findByEmailIgnoreCase(secondOwner)).isEmpty();
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void create_takenOwnerEmail_isEmailTaken_andRollsTheRestaurantBack() throws Exception {
    User existing = testUsers.owner(uniqueEmail(), PASSWORD);
    String slug = uniqueSlug();

    create(admin, createBody("Taken", slug, null, existing.getEmail()))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

    assertThat(restaurantRepository.existsBySlug(slug)).as("the restaurant is rolled back").isFalse();
    assertThat(auditEventRepository.findAll())
      .noneMatch(event -> event.getType() == IdentityAuditEventType.RESTAURANT_CREATED
        && slug.equals(event.getDetails().get("slug")));
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void create_reservedSlug_isSlugReserved() throws Exception {
    for (String slug : List.of("admin", "api", "app", "auth", "login", "platform", "public", "static", "www")) {
      create(admin, createBody("Reserved", slug, null, uniqueEmail()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SLUG_RESERVED"));
    }
  }

  @Test
  void create_malformedSlug_isInvalidInput() throws Exception {
    for (String slug : List.of("ab", "Upper-case", "two--hyphens", "-leading", "trailing-", "with space", "acentuação",
      "a".repeat(41))) {
      create(admin, createBody("Bad slug", slug, null, uniqueEmail()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
  }

  @Test
  void create_unknownTimezone_isInvalidInput() throws Exception {
    String slug = uniqueSlug();
    create(admin, createBody("Zone", slug, "Mars/Olympus", uniqueEmail()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    assertThat(restaurantRepository.existsBySlug(slug)).isFalse();
  }

  @Test
  void create_missingOrInvalidFields_isInvalidInput() throws Exception {
    Map<String, Object> noOwner = createBody("No owner", uniqueSlug(), null, uniqueEmail());
    noOwner.remove("owner");
    Map<String, Object> badEmail = createBody("Bad email", uniqueSlug(), null, "not-an-email");
    Map<String, Object> blankName = createBody("  ", uniqueSlug(), null, uniqueEmail());
    Map<String, Object> withRestaurantId = createBody("Extra", uniqueSlug(), null, uniqueEmail());
    withRestaurantId.put("restaurantId", UNKNOWN_RESTAURANT);

    for (Map<String, Object> body : List.of(noOwner, badEmail, blankName, withRestaurantId)) {
      create(admin, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
  }

  // ---- get, list, update ----

  @Test
  void get_returnsTheRestaurant_andUnknownIsNotFound() throws Exception {
    String slug = uniqueSlug();
    String restaurantId = createdId(create(admin, createBody("Get me", slug, null, uniqueEmail())));
    String token = bearer(admin);

    mockMvc.perform(get("/platform/restaurants/{id}", restaurantId).header("Authorization", token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(restaurantId))
      .andExpect(jsonPath("$.slug").value(slug))
      .andExpect(jsonPath("$.storefrontUrl").value("https://order.example.com/r/" + slug));
    mockMvc.perform(get("/platform/restaurants/{id}", UNKNOWN_RESTAURANT).header("Authorization", token))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    mockMvc.perform(get("/platform/restaurants/{id}", "not-a-ulid").header("Authorization", token))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void list_returnsEveryRestaurant_byNameByDefault_withPageFields() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String beta = createdId(create(admin, createBody("Beta " + suffix, uniqueSlug(), null, uniqueEmail())));
    String alpha = createdId(create(admin, createBody("Alpha " + suffix, uniqueSlug(), null, uniqueEmail())));
    String memberRestaurant = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));
    String token = bearer(admin);

    mockMvc.perform(get("/platform/restaurants").header("Authorization", token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalItems").isNumber())
      .andExpect(jsonPath("$.totalPages").isNumber());

    List<String> byName = allIds(token, null);
    assertThat(byName).contains(memberRestaurant);
    assertThat(byName.indexOf(alpha)).isLessThan(byName.indexOf(beta));

    List<String> byNameDesc = allIds(token, "name,desc");
    assertThat(byNameDesc.indexOf(beta)).isLessThan(byNameDesc.indexOf(alpha));

    List<String> byCreatedAt = allIds(token, "createdAt,asc");
    assertThat(byCreatedAt.indexOf(beta)).isLessThan(byCreatedAt.indexOf(alpha));
  }

  @Test
  void list_badPageSizeOrSort_isInvalidInput() throws Exception {
    String token = bearer(admin);
    for (Map.Entry<String, String> param : List.of(
      Map.entry("size", "101"), Map.entry("size", "0"), Map.entry("page", "-1"),
      Map.entry("sort", "slug,asc"), Map.entry("sort", "name,up"), Map.entry("sort", "name,asc,id"))) {
      mockMvc.perform(get("/platform/restaurants").param(param.getKey(), param.getValue()).header("Authorization", token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
  }

  @Test
  void update_changesNameAndTimezone_andAuditsIntoTheRestaurantsLog() throws Exception {
    String restaurantId = createdId(create(admin, createBody("Old name", uniqueSlug(), null, uniqueEmail())));

    update(admin, restaurantId, Map.of("name", "  New name ", "timezone", "America/Manaus"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("New name"))
      .andExpect(jsonPath("$.timezone").value("America/Manaus"));

    assertThat(eventsOf(restaurantId))
      .filteredOn(event -> event.getType() == IdentityAuditEventType.RESTAURANT_UPDATED)
      .singleElement()
      .satisfies(event -> {
        assertThat(event.getActorUserId()).isEqualTo(admin.getId());
        assertThat(event.getTargetUserId()).isNull();
        assertThat(event.getDetails()).isEqualTo(Map.of(
          "name", Map.of("from", "Old name", "to", "New name"),
          "timezone", Map.of("from", "America/Sao_Paulo", "to", "America/Manaus")));
      });
  }

  @Test
  void update_theOwnerSeesTheCorrectedTimezone() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);

    update(admin, restaurantId, Map.of("timezone", "America/Recife")).andExpect(status().isOk());

    mockMvc.perform(get("/restaurant").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.timezone").value("America/Recife"));
  }

  @Test
  void update_onlyTheName_keepsTheTimezone_andANoOpWritesNoEvent() throws Exception {
    String restaurantId = createdId(create(admin, createBody("Same", uniqueSlug(), "Europe/Lisbon", uniqueEmail())));

    update(admin, restaurantId, Map.of("name", "Same"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Same"))
      .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
    update(admin, restaurantId, Map.of())
      .andExpect(status().isOk());

    assertThat(eventsOf(restaurantId)).noneMatch(event -> event.getType() == IdentityAuditEventType.RESTAURANT_UPDATED);
  }

  @Test
  void update_invalidInput_isRejected() throws Exception {
    String restaurantId = createdId(create(admin, createBody("Valid", uniqueSlug(), null, uniqueEmail())));

    for (Map<String, Object> body : List.<Map<String, Object>>of(
      Map.of("timezone", "Mars/Olympus"), Map.of("timezone", " "), Map.of("name", " "), Map.of("name", "x".repeat(81)),
      Map.of("slug", "new-slug"), Map.of("currency", "USD"))) {
      update(admin, restaurantId, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
  }

  @Test
  void update_unknownRestaurant_isNotFound() throws Exception {
    update(admin, UNKNOWN_RESTAURANT, Map.of("name", "Ghost"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
  }

  // ---- members ----

  @Test
  void listMembers_returnsThatRestaurantsTeamOnly() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);
    User staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);
    User otherOwner = testUsers.owner(uniqueEmail(), PASSWORD);

    String body = mockMvc.perform(get("/platform/restaurants/{id}/members", restaurantId)
        .header("Authorization", bearer(admin)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(2))
      .andExpect(jsonPath("$.items[0].id").value(owner.getId()))
      .andExpect(jsonPath("$.items[0].role").value("OWNER"))
      .andExpect(jsonPath("$.items[1].id").value(staff.getId()))
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain(otherOwner.getId(), admin.getId());
  }

  @Test
  void listMembers_unknownRestaurant_isNotFound() throws Exception {
    mockMvc.perform(get("/platform/restaurants/{id}/members", UNKNOWN_RESTAURANT).header("Authorization", bearer(admin)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
  }

  @Test
  void inviteMember_anyRole_includingASecondOwner() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);

    for (String role : List.of("OWNER", "MANAGER", "STAFF")) {
      String email = uniqueEmail();
      inviteMember(admin, restaurantId, email, role)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value(role))
        .andExpect(jsonPath("$.invitationPending").value(true));
      User invitee = userRepository.findByEmailIgnoreCase(email).orElseThrow();
      assertThat(membershipRepository.findByUserId(invitee.getId()).orElseThrow().getRestaurantId())
        .isEqualTo(restaurantId);
    }

    assertThat(emails.invitations()).hasSize(3);
    assertThat(eventsOf(restaurantId))
      .filteredOn(event -> event.getType() == IdentityAuditEventType.MEMBER_INVITED)
      .hasSize(3)
      .allMatch(event -> admin.getId().equals(event.getActorUserId()));
  }

  @Test
  void inviteMember_takenEmail_isEmailTaken() throws Exception {
    String restaurantId = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));
    User elsewhere = testUsers.owner(uniqueEmail(), PASSWORD);

    inviteMember(admin, restaurantId, elsewhere.getEmail(), "STAFF")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void inviteMember_unknownRestaurant_isNotFound() throws Exception {
    String email = uniqueEmail();
    inviteMember(admin, UNKNOWN_RESTAURANT, email, "OWNER")
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
  }

  // ---- who may call ----

  @Test
  void everyOperation_asEachMemberRole_isForbidden() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);
    User manager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    User staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    for (User member : List.of(owner, manager, staff)) {
      String token = bearer(member);
      for (MockHttpServletRequestBuilder request : everyOperation(restaurantId)) {
        mockMvc.perform(request.header("Authorization", token))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
      }
    }
    assertThat(restaurantRepository.findById(restaurantId).orElseThrow().getName()).isEqualTo("Test Restaurant");
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void everyOperation_anonymous_isUnauthorized() throws Exception {
    for (MockHttpServletRequestBuilder request : everyOperation(UNKNOWN_RESTAURANT)) {
      mockMvc.perform(request)
        .andExpect(status().isUnauthorized());
    }
  }

  // §5.4: the support header is for GET only; a member never gets it.
  @Test
  void supportHeader_onAPlatformWrite_isForbidden() throws Exception {
    String restaurantId = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(patch("/platform/restaurants/{id}", restaurantId)
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("name", "Hijacked"))))
      .andExpect(status().isForbidden());
    assertThat(restaurantRepository.findById(restaurantId).orElseThrow().getName()).isEqualTo("Test Restaurant");
  }

  @Test
  void supportHeader_fromAnOwner_onAPlatformRead_isForbidden() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String otherRestaurant = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(get("/platform/restaurants/{id}", otherRestaurant)
        .header("Authorization", bearer(owner))
        .header(TenantContextFilter.SUPPORT_HEADER, otherRestaurant))
      .andExpect(status().isForbidden());
  }

  private List<MockHttpServletRequestBuilder> everyOperation(String restaurantId) throws Exception {
    return List.of(
      get("/platform/restaurants"),
      post("/platform/restaurants").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createBody("Nope", uniqueSlug(), null, uniqueEmail()))),
      get("/platform/restaurants/{id}", restaurantId),
      patch("/platform/restaurants/{id}", restaurantId).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("name", "Hijacked"))),
      get("/platform/restaurants/{id}/members", restaurantId),
      post("/platform/restaurants/{id}/members", restaurantId).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(inviteBody(uniqueEmail(), "OWNER"))));
  }

  // ---- helpers ----

  private ResultActions create(User caller, Map<String, Object> body) throws Exception {
    return mockMvc.perform(post("/platform/restaurants")
      .header("Authorization", bearer(caller))
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(body)));
  }

  private ResultActions update(User caller, String restaurantId, Map<String, Object> body) throws Exception {
    return mockMvc.perform(patch("/platform/restaurants/{id}", restaurantId)
      .header("Authorization", bearer(caller))
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(body)));
  }

  private ResultActions inviteMember(User caller, String restaurantId, String email, String role) throws Exception {
    return mockMvc.perform(post("/platform/restaurants/{id}/members", restaurantId)
      .header("Authorization", bearer(caller))
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(inviteBody(email, role))));
  }

  private static Map<String, Object> createBody(String name, String slug, String timezone, String ownerEmail) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("slug", slug);
    if (timezone != null) {
      body.put("timezone", timezone);
    }
    body.put("owner", Map.of("email", ownerEmail, "firstName", "Ana", "lastName", "Souza"));
    return body;
  }

  private static Map<String, Object> inviteBody(String email, String role) {
    return Map.of("email", email, "firstName", "Bruno", "lastName", "Lima", "role", role);
  }

  private String createdId(ResultActions result) throws Exception {
    String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("id").asText();
  }

  // Every restaurant id in the requested order, page by page (other tests add restaurants too).
  private List<String> allIds(String token, String sort) throws Exception {
    List<String> ids = new ArrayList<>();
    for (int page = 0; ; page++) {
      MockHttpServletRequestBuilder request = get("/platform/restaurants")
        .param("page", String.valueOf(page)).param("size", "100").header("Authorization", token);
      if (sort != null) {
        request.param("sort", sort);
      }
      JsonNode body = objectMapper.readTree(mockMvc.perform(request)
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
      body.get("items").forEach(item -> ids.add(item.get("id").asText()));
      if (page + 1 >= body.get("totalPages").asInt()) {
        return ids;
      }
    }
  }

  private IdentityAuditEvent singleEvent(IdentityAuditEventType type, String restaurantIdInDetails) {
    return auditEventRepository.findAll().stream()
      .filter(event -> event.getType() == type && restaurantIdInDetails.equals(event.getDetails().get("restaurantId")))
      .reduce((first, second) -> {
        throw new AssertionError("more than one " + type + " event for " + restaurantIdInDetails);
      })
      .orElseThrow(() -> new AssertionError("no " + type + " event for " + restaurantIdInDetails));
  }

  private List<IdentityAuditEvent> eventsOf(String restaurantId) {
    return auditEventRepository.findAll().stream()
      .filter(event -> restaurantId.equals(event.getRestaurantId()))
      .toList();
  }

  private String bearer(User user) throws Exception {
    return bearer(user.getEmail(), PASSWORD);
  }

  private String bearer(String email, String password) throws Exception {
    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, password))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
  }

  private static String tokenOf(String setupUrl) {
    return setupUrl.substring(setupUrl.indexOf("token=") + "token=".length());
  }

  private static String uniqueSlug() {
    return "t-" + UUID.randomUUID();
  }

  private static String uniqueEmail() {
    return "platform-" + UUID.randomUUID() + "@test.com";
  }
}
