package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.identity.application.IdentityAuditService;
import com.enterprise.ordersuite.security.web.TenantContextFilter;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /audit-events (Tenancy & Identity, Audit event; §5.3, §5.6; acceptance 7 and 8).
@IntegrationTest
@AutoConfigureMockMvc
class AuditEventsIT {

  private static final String PASSWORD = "Password123!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private IdentityAuditService identityAuditService;

  private User owner;
  private User manager;
  private User staff;
  private String restaurantId;

  @BeforeEach
  void team() {
    owner = testUsers.owner(uniqueEmail(), PASSWORD);
    restaurantId = testUsers.restaurantOf(owner);
    manager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);
    User named = userRepository.findById(staff.getId()).orElseThrow();
    named.setFirstName("Ana");
    named.setLastName("Souza");
    userRepository.save(named);
  }

  @Test
  void list_asOwner_returnsTheRestaurantsEvents_newestFirst_withNames() throws Exception {
    // Through the API, so the events are written the way production writes them.
    mockMvc.perform(post("/team/members/{id}/deactivate", staff.getId()).header("Authorization", bearer(owner)))
      .andExpect(status().isOk());
    mockMvc.perform(post("/team/members/{id}/reactivate", staff.getId()).header("Authorization", bearer(owner)))
      .andExpect(status().isOk());

    mockMvc.perform(get("/audit-events").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(2))
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalPages").value(1))
      .andExpect(jsonPath("$.items[0].type").value("MEMBER_REACTIVATED"))
      .andExpect(jsonPath("$.items[1].type").value("MEMBER_DEACTIVATED"))
      .andExpect(jsonPath("$.items[0].id").isNotEmpty())
      .andExpect(jsonPath("$.items[0].restaurantId").value(restaurantId))
      .andExpect(jsonPath("$.items[0].actorUserId").value(owner.getId()))
      .andExpect(jsonPath("$.items[0].actorName").value("Test User"))
      .andExpect(jsonPath("$.items[0].targetUserId").value(staff.getId()))
      .andExpect(jsonPath("$.items[0].targetName").value("Ana Souza"))
      .andExpect(jsonPath("$.items[0].details").isMap())
      .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty());
  }

  @Test
  void list_filtersByType_andShowsDetails() throws Exception {
    identityAuditService.recordEvent(IdentityAuditEventType.ROLE_CHANGED, restaurantId, owner.getId(), staff.getId(),
      Map.of("from", "STAFF", "to", "MANAGER"));
    identityAuditService.recordEvent(IdentityAuditEventType.MEMBER_DEACTIVATED, restaurantId, owner.getId(), staff.getId(),
      Map.of());

    mockMvc.perform(get("/audit-events").param("type", "ROLE_CHANGED").header("Authorization", bearer(manager)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(1))
      .andExpect(jsonPath("$.items[0].details.from").value("STAFF"))
      .andExpect(jsonPath("$.items[0].details.to").value("MANAGER"));
  }

  @Test
  void list_eventWithoutATarget_hasNullTargetFields() throws Exception {
    identityAuditService.recordEvent(IdentityAuditEventType.RESTAURANT_UPDATED, restaurantId, owner.getId(), null,
      Map.of("name", Map.of("from", "A", "to", "B")));

    mockMvc.perform(get("/audit-events").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].targetUserId").isEmpty())
      .andExpect(jsonPath("$.items[0].targetName").isEmpty())
      .andExpect(jsonPath("$.items[0].details.name.to").value("B"));
  }

  @Test
  void list_neverContainsAnotherRestaurantsOrPlatformLevelEvents() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);
    identityAuditService.recordEvent(IdentityAuditEventType.MEMBER_DEACTIVATED, restaurantId, owner.getId(), staff.getId(),
      Map.of());
    identityAuditService.recordEvent(IdentityAuditEventType.RESTAURANT_CREATED, null, owner.getId(), null, Map.of());

    String body = mockMvc.perform(get("/audit-events").header("Authorization", bearer(ownerR2)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(0))
      .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain(staff.getId());

    mockMvc.perform(get("/audit-events").header("Authorization", bearer(owner)))
      .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void list_asStaff_isForbidden() throws Exception {
    mockMvc.perform(get("/audit-events").header("Authorization", bearer(staff)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void list_platformAdmin_needsTheSupportHeader() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    identityAuditService.recordEvent(IdentityAuditEventType.MEMBER_DEACTIVATED, restaurantId, owner.getId(), staff.getId(),
      Map.of());

    mockMvc.perform(get("/audit-events").header("Authorization", bearer(admin)))
      .andExpect(status().isForbidden());
    mockMvc.perform(get("/audit-events")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void list_memberWithSupportHeader_isForbidden() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/audit-events")
        .header("Authorization", bearer(ownerR2))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isForbidden());
  }

  @Test
  void list_badSizeOrType_is400() throws Exception {
    String token = bearer(owner);

    mockMvc.perform(get("/audit-events").param("size", "101").header("Authorization", token))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mockMvc.perform(get("/audit-events").param("type", "SOMETHING").header("Authorization", token))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
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
    return "audit-" + UUID.randomUUID() + "@test.com";
  }
}
