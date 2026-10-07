package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.common.tenancy.TenantContext;
import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tenancy & Identity §5.1 and §5.4; acceptance 8 and 10. No restaurant endpoint exists yet
// (slices 5-7), so a test-only controller reports what the request sees.
@IntegrationTest
@AutoConfigureMockMvc
@Import(TenantContextIT.ProbeController.class)
class TenantContextIT {

  private static final String PASSWORD = "Password123!";
  private static final String OTHER_RESTAURANT = "01J00000000000000000000000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private MembershipRepository membershipRepository;

  @Test
  void member_getsTheRestaurantFromTheirToken_andItIsInTheLogMdc() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = restaurantOf(owner);

    mockMvc.perform(get("/test/tenant").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.restaurantId").value(restaurantId))
      .andExpect(jsonPath("$.userId").value(owner.getId()))
      .andExpect(jsonPath("$.role").value("OWNER"))
      .andExpect(jsonPath("$.support").value(false))
      .andExpect(jsonPath("$.mdcRestaurantId").value(restaurantId))
      .andExpect(jsonPath("$.mdcUserId").value(owner.getId()))
      .andExpect(jsonPath("$.mdcRequestId").isNotEmpty())
      .andExpect(jsonPath("$.mdcSupport").doesNotExist());
  }

  @Test
  void anonymous_getsNoTenantContext_andIs401() throws Exception {
    mockMvc.perform(get("/test/tenant"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void member_withSupportHeader_isForbidden() throws Exception {
    User staff = testUsers.member(uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    mockMvc.perform(get("/test/tenant")
        .header("Authorization", bearer(staff))
        .header(TenantContextFilter.SUPPORT_HEADER, OTHER_RESTAURANT))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void owner_withSupportHeader_isForbidden_soTheHeaderCannotReachAnotherRestaurant() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/test/tenant")
        .header("Authorization", bearer(owner))
        .header(TenantContextFilter.SUPPORT_HEADER, OTHER_RESTAURANT))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void platformAdmin_getWithSupportHeader_readsThatRestaurant() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String restaurantId = restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(get("/test/tenant")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.restaurantId").value(restaurantId))
      .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
      .andExpect(jsonPath("$.support").value(true))
      .andExpect(jsonPath("$.mdcRestaurantId").value(restaurantId))
      .andExpect(jsonPath("$.mdcUserId").value(admin.getId()))
      .andExpect(jsonPath("$.mdcSupport").value("true"));
  }

  @Test
  void platformAdmin_getWithSupportHeader_underContextPath_readsThatRestaurant() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String restaurantId = restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(get("/api/test/tenant").contextPath("/api")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.restaurantId").value(restaurantId))
      .andExpect(jsonPath("$.support").value(true));
  }

  @Test
  void platformAdmin_writeWithSupportHeader_isForbidden() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String restaurantId = restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(post("/test/tenant")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void platformAdmin_malformedSupportHeader_isForbidden() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/test/tenant")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, "not-a-ulid"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void platformAdmin_withoutSupportHeader_restaurantScopedCodeFailsClosed() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/test/tenant/restaurant").header("Authorization", bearer(admin)))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
  }

  @Test
  void member_restaurantScopedCode_getsTheirRestaurant() throws Exception {
    User staff = testUsers.member(uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    mockMvc.perform(get("/test/tenant/restaurant").header("Authorization", bearer(staff)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.restaurantId").value(restaurantOf(staff)));
  }

  @Test
  void managerEndpoint_owner_isAllowedThroughTheHierarchy() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/test/manager-only").header("Authorization", bearer(owner)))
      .andExpect(status().isOk());
  }

  @Test
  void managerEndpoint_manager_isAllowed() throws Exception {
    User manager = testUsers.member(uniqueEmail(), PASSWORD, MembershipRole.MANAGER);

    mockMvc.perform(get("/test/manager-only").header("Authorization", bearer(manager)))
      .andExpect(status().isOk());
  }

  @Test
  void managerEndpoint_staff_isForbidden() throws Exception {
    User staff = testUsers.member(uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    mockMvc.perform(get("/test/manager-only").header("Authorization", bearer(staff)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void managerEndpoint_platformAdmin_isForbidden_becauseItSitsOutsideTheHierarchy() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/test/manager-only").header("Authorization", bearer(admin)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  private String restaurantOf(User user) {
    return membershipRepository.findByUserId(user.getId()).orElseThrow().getRestaurantId();
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
    return "tenant-" + UUID.randomUUID() + "@test.com";
  }

  @RestController
  static class ProbeController {

    @GetMapping("/test/tenant")
    Map<String, Object> read() {
      return describe();
    }

    @PostMapping("/test/tenant")
    Map<String, Object> write() {
      return describe();
    }

    @GetMapping("/test/tenant/restaurant")
    Map<String, Object> restaurant() {
      return Map.of("restaurantId", TenantContextHolder.requireRestaurantId());
    }

    @GetMapping("/test/manager-only")
    @PreAuthorize("hasRole('MANAGER')")
    Map<String, Object> managerOnly() {
      return Map.of("ok", true);
    }

    private static Map<String, Object> describe() {
      TenantContext context = TenantContextHolder.get().orElseThrow();
      Map<String, Object> body = new HashMap<>();
      body.put("restaurantId", context.restaurantId());
      body.put("userId", context.userId());
      body.put("role", context.role());
      body.put("support", context.support());
      body.put("mdcRestaurantId", MDC.get("restaurantId"));
      body.put("mdcUserId", MDC.get("userId"));
      body.put("mdcRequestId", MDC.get("requestId"));
      body.put("mdcSupport", MDC.get("support"));
      return body;
    }
  }
}
