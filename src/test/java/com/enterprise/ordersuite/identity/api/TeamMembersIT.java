package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.security.web.TenantContextFilter;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.RefreshCookies;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /team/members* (Tenancy & Identity §5.3, Invites, Membership changes, §5.6; acceptance 3,
// 4, 5, 7, 8). Each test builds its own restaurant: owner, manager and staff, plus R2 for the
// cross-tenant cases.
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestEmailServiceConfig.class)
class TeamMembersIT {

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
  private MembershipRepository membershipRepository;

  @Autowired
  private IdentityAuditEventRepository auditEventRepository;

  @Autowired
  private TestEmailServiceConfig.CapturingEmailService emails;

  private User owner;
  private User manager;
  private User staff;
  private String restaurantId;

  @BeforeEach
  void team() {
    emails.clear();
    owner = testUsers.owner(uniqueEmail(), PASSWORD);
    restaurantId = testUsers.restaurantOf(owner);
    manager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);
  }

  // ---- list and get ----

  @Test
  void list_asOwner_returnsTheTeamByRoleThenLastName_withPageFields() throws Exception {
    User secondStaff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);
    rename(staff, "Zeta");
    rename(secondStaff, "Alpha");

    mockMvc.perform(get("/team/members").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(4))
      .andExpect(jsonPath("$.items[0].id").value(owner.getId()))
      .andExpect(jsonPath("$.items[0].role").value("OWNER"))
      .andExpect(jsonPath("$.items[1].id").value(manager.getId()))
      .andExpect(jsonPath("$.items[2].id").value(secondStaff.getId()))
      .andExpect(jsonPath("$.items[3].id").value(staff.getId()))
      .andExpect(jsonPath("$.items[0].email").value(owner.getEmail()))
      .andExpect(jsonPath("$.items[0].active").value(true))
      .andExpect(jsonPath("$.items[0].invitationPending").value(false))
      .andExpect(jsonPath("$.items[0].memberSince").isNotEmpty())
      .andExpect(jsonPath("$.items[0].phone").isEmpty())
      .andExpect(jsonPath("$.items[0].avatarUrl").isEmpty())
      .andExpect(jsonPath("$.items[0].password").doesNotExist())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalItems").value(4))
      .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void list_asManager_isAllowed() throws Exception {
    mockMvc.perform(get("/team/members").header("Authorization", bearer(manager)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(3));
  }

  @Test
  void list_asStaff_isForbidden() throws Exception {
    mockMvc.perform(get("/team/members").header("Authorization", bearer(staff)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void list_neverContainsAnotherRestaurantsMembers() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    String body = mockMvc.perform(get("/team/members").header("Authorization", bearer(ownerR2)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(1))
      .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(owner.getId(), manager.getId(), staff.getId());
  }

  @Test
  void list_filtersByRoleAndActive() throws Exception {
    deactivateDirectly(staff);
    testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    mockMvc.perform(get("/team/members").param("role", "STAFF").header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(2));
    mockMvc.perform(get("/team/members").param("role", "STAFF").param("active", "false")
        .header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(1))
      .andExpect(jsonPath("$.items[0].id").value(staff.getId()));
  }

  @Test
  void list_sortsByLastNameDescending_andPages() throws Exception {
    rename(owner, "Bravo");
    rename(manager, "Alpha");
    rename(staff, "Charlie");

    mockMvc.perform(get("/team/members").param("sort", "lastName,desc").param("size", "2")
        .header("Authorization", bearer(owner)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(2))
      .andExpect(jsonPath("$.items[0].lastName").value("Charlie"))
      .andExpect(jsonPath("$.items[1].lastName").value("Bravo"))
      .andExpect(jsonPath("$.totalItems").value(3))
      .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void list_badPageSizeSortOrRole_is400() throws Exception {
    String token = bearer(owner);

    for (String[] param : List.of(
      new String[]{"size", "101"},
      new String[]{"size", "0"},
      new String[]{"page", "-1"},
      new String[]{"sort", "email,asc"},
      new String[]{"sort", "lastName,sideways"},
      new String[]{"role", "BOSS"})) {
      mockMvc.perform(get("/team/members").param(param[0], param[1]).header("Authorization", token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
  }

  @Test
  void list_platformAdmin_needsTheSupportHeader() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/team/members").header("Authorization", bearer(admin)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc.perform(get("/team/members")
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalItems").value(3));
  }

  @Test
  void list_memberWithSupportHeader_isForbidden() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/team/members")
        .header("Authorization", bearer(ownerR2))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void get_asManager_returnsTheMember() throws Exception {
    mockMvc.perform(get("/team/members/{id}", staff.getId()).header("Authorization", bearer(manager)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(staff.getId()))
      .andExpect(jsonPath("$.role").value("STAFF"));
  }

  @Test
  void get_asStaff_isForbidden() throws Exception {
    mockMvc.perform(get("/team/members/{id}", manager.getId()).header("Authorization", bearer(staff)))
      .andExpect(status().isForbidden());
  }

  @Test
  void get_anotherRestaurantsMember_is404() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/team/members/{id}", staff.getId()).header("Authorization", bearer(ownerR2)))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  @Test
  void get_malformedId_is400() throws Exception {
    mockMvc.perform(get("/team/members/{id}", "not-a-ulid").header("Authorization", bearer(owner)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void get_platformAdminSupportRead_returnsTheMember() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);

    mockMvc.perform(get("/team/members/{id}", staff.getId())
        .header("Authorization", bearer(admin))
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(staff.getId()));
  }

  // ---- invite ----

  @Test
  void invite_asOwner_createsAPendingMember_emailsAfterCommit_andTheInviteeCanSignIn() throws Exception {
    String email = uniqueEmail();

    String body = invite(owner, email.toUpperCase(), "MANAGER")
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.firstName").value("New"))
      .andExpect(jsonPath("$.role").value("MANAGER"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.invitationPending").value(true))
      .andReturn().getResponse().getContentAsString();
    String memberId = objectMapper.readTree(body).get("id").asText();

    assertThat(membershipRepository.findMember(restaurantId, memberId)).isPresent();
    assertThat(emails.invitations()).hasSize(1);
    assertThat(emails.invitations().get(0).toEmail()).isEqualTo(email);
    assertAudited(IdentityAuditEventType.MEMBER_INVITED, owner, memberId, Map.of("role", "MANAGER"));

    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
          "token", tokenOf(emails.invitations().get(0).resetUrl()), "newPassword", "Brand-new-pass1"))))
      .andExpect(status().is2xxSuccessful());
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, "Brand-new-pass1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.role").value("MANAGER"))
      .andExpect(jsonPath("$.user.restaurantId").value(restaurantId));
  }

  @Test
  void invite_asManager_mayInviteStaff() throws Exception {
    invite(manager, uniqueEmail(), "STAFF")
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.role").value("STAFF"));
  }

  @Test
  void invite_asManager_mayNotInviteAManagerOrAnOwner() throws Exception {
    for (String role : List.of("MANAGER", "OWNER")) {
      invite(manager, uniqueEmail(), role)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void invite_asStaff_isForbidden() throws Exception {
    invite(staff, uniqueEmail(), "STAFF")
      .andExpect(status().isForbidden());
  }

  @Test
  void invite_existingEmail_evenInAnotherRestaurant_is409_withoutNamingIt() throws Exception {
    User elsewhere = testUsers.owner(uniqueEmail(), PASSWORD);

    invite(owner, elsewhere.getEmail().toUpperCase(), "STAFF")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
      .andExpect(jsonPath("$.message").value("This email is already in use"));
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void invite_withARestaurantIdInTheBody_isRejectedAsUnknown() throws Exception {
    String otherRestaurant = testUsers.restaurantOf(testUsers.owner(uniqueEmail(), PASSWORD));

    mockMvc.perform(post("/team/members")
        .header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
          "email", uniqueEmail(), "firstName", "A", "lastName", "B", "role", "STAFF",
          "restaurantId", otherRestaurant))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
      .andExpect(jsonPath("$.errors[0]").value("restaurantId: unknown field"));
  }

  @Test
  void invite_missingRole_is400() throws Exception {
    mockMvc.perform(post("/team/members")
        .header("Authorization", bearer(owner))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", uniqueEmail(), "firstName", "A", "lastName", "B"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void invite_platformAdmin_isForbidden_withOrWithoutTheSupportHeader() throws Exception {
    User admin = testUsers.platformAdmin(uniqueEmail(), PASSWORD);
    String token = bearer(admin);

    mockMvc.perform(post("/team/members")
        .header("Authorization", token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(inviteBody(uniqueEmail(), "STAFF")))
      .andExpect(status().isForbidden());
    mockMvc.perform(post("/team/members")
        .header("Authorization", token)
        .header(TenantContextFilter.SUPPORT_HEADER, restaurantId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(inviteBody(uniqueEmail(), "STAFF")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  // ---- role change and name ----

  @Test
  void patch_ownerPromotesStaff_revokesTheirSessions_andAudits() throws Exception {
    MvcResult staffLogin = login(staff);

    patchMember(owner, staff, Map.of("role", "MANAGER"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.role").value("MANAGER"));

    assertThat(membershipRepository.findMember(restaurantId, staff.getId()).orElseThrow().getRole())
      .isEqualTo(MembershipRole.MANAGER);
    assertAudited(IdentityAuditEventType.ROLE_CHANGED, owner, staff.getId(), Map.of("from", "STAFF", "to", "MANAGER"));
    mockMvc.perform(RefreshCookies.refresh(RefreshCookies.valueOf(staffLogin)))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void patch_sameRole_isANoOp_withoutAuditOrRevocation() throws Exception {
    MvcResult staffLogin = login(staff);

    patchMember(owner, staff, Map.of("role", "STAFF"))
      .andExpect(status().isOk());

    assertThat(eventsOf(restaurantId)).isEmpty();
    mockMvc.perform(RefreshCookies.refresh(RefreshCookies.valueOf(staffLogin)))
      .andExpect(status().isOk());
  }

  @Test
  void patch_managerRenamesStaff() throws Exception {
    patchMember(manager, staff, Map.of("firstName", "  Ana ", "lastName", "Souza"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Ana"))
      .andExpect(jsonPath("$.lastName").value("Souza"))
      .andExpect(jsonPath("$.role").value("STAFF"));
  }

  @Test
  void patch_managerPromotingStaff_isForbidden() throws Exception {
    for (String role : List.of("MANAGER", "OWNER")) {
      patchMember(manager, staff, Map.of("role", role))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertThat(membershipRepository.findMember(restaurantId, staff.getId()).orElseThrow().getRole())
      .isEqualTo(MembershipRole.STAFF);
  }

  @Test
  void patch_managerActingOnAManagerOrTheOwner_isForbidden() throws Exception {
    User otherManager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);

    for (User target : List.of(otherManager, owner)) {
      patchMember(manager, target, Map.of("firstName", "Renamed"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
  }

  @Test
  void patch_onlyOwnerDemotingThemselves_isLastOwner() throws Exception {
    patchMember(owner, owner, Map.of("role", "MANAGER"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("LAST_OWNER"));
  }

  @Test
  void patch_ownerWithACoOwnerChangingTheirOwnRole_isSelfActionNotAllowed() throws Exception {
    testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);

    patchMember(owner, owner, Map.of("role", "MANAGER"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SELF_ACTION_NOT_ALLOWED"));
  }

  @Test
  void patch_ownerDemotesACoOwner() throws Exception {
    User coOwner = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);

    patchMember(owner, coOwner, Map.of("role", "MANAGER"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  void patch_anotherRestaurantsMember_is404_andChangesNothing() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    patchMember(ownerR2, staff, Map.of("role", "OWNER"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    assertThat(membershipRepository.findMember(restaurantId, staff.getId()).orElseThrow().getRole())
      .isEqualTo(MembershipRole.STAFF);
  }

  @Test
  void patch_unknownField_is400() throws Exception {
    patchMember(owner, staff, Map.of("email", "new@test.com"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0]").value("email: unknown field"));
  }

  // ---- deactivate and reactivate ----

  @Test
  void deactivate_ownerDeactivatesStaff_whoseRefreshAndLoginThenFail() throws Exception {
    MvcResult staffLogin = login(staff);

    command(owner, "/team/members/{id}/deactivate", staff)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false));

    assertAudited(IdentityAuditEventType.MEMBER_DEACTIVATED, owner, staff.getId(), Map.of());
    mockMvc.perform(RefreshCookies.refresh(RefreshCookies.valueOf(staffLogin)))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(staff.getEmail(), PASSWORD))))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void deactivate_repeated_isANoOp() throws Exception {
    command(owner, "/team/members/{id}/deactivate", staff).andExpect(status().isOk());
    command(owner, "/team/members/{id}/deactivate", staff)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false));

    assertThat(eventsOf(restaurantId)).hasSize(1);
  }

  @Test
  void deactivate_onlyOwnerDeactivatingThemselves_isLastOwner() throws Exception {
    command(owner, "/team/members/{id}/deactivate", owner)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("LAST_OWNER"));
  }

  @Test
  void deactivate_ownerWithACoOwnerDeactivatingThemselves_isSelfActionNotAllowed() throws Exception {
    testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);

    command(owner, "/team/members/{id}/deactivate", owner)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SELF_ACTION_NOT_ALLOWED"));
  }

  @Test
  void deactivate_lastActiveOwner_withAnInactiveCoOwner_isLastOwner() throws Exception {
    User coOwner = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);
    String coOwnerToken = bearer(coOwner);
    deactivateDirectly(coOwner);
    // The co-owner's token is still valid, but TeamAccess judges them by today's membership.
    command(coOwnerToken, "/team/members/{id}/deactivate", owner)
      .andExpect(status().isForbidden());

    command(owner, "/team/members/{id}/deactivate", owner)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("LAST_OWNER"));
  }

  @Test
  void deactivate_managerOnStaff_isAllowed_onAManagerOrOwner_isForbidden() throws Exception {
    User otherManager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);

    command(manager, "/team/members/{id}/deactivate", staff).andExpect(status().isOk());
    command(manager, "/team/members/{id}/deactivate", otherManager).andExpect(status().isForbidden());
    command(manager, "/team/members/{id}/deactivate", owner).andExpect(status().isForbidden());
    command(manager, "/team/members/{id}/deactivate", manager).andExpect(status().isForbidden());
  }

  @Test
  void deactivate_asStaff_isForbidden() throws Exception {
    User otherStaff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);

    command(staff, "/team/members/{id}/deactivate", otherStaff).andExpect(status().isForbidden());
  }

  @Test
  void deactivate_anotherRestaurantsMember_is404() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);

    command(ownerR2, "/team/members/{id}/deactivate", staff)
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    assertThat(userRepository.findById(staff.getId()).orElseThrow().getActive()).isTrue();
  }

  @Test
  void reactivate_ownerReactivatesStaff_whoCanSignInAgain() throws Exception {
    command(owner, "/team/members/{id}/deactivate", staff).andExpect(status().isOk());

    command(owner, "/team/members/{id}/reactivate", staff)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(true));

    assertAudited(IdentityAuditEventType.MEMBER_REACTIVATED, owner, staff.getId(), Map.of());
    login(staff);
  }

  @Test
  void reactivate_managerOnAManager_isForbidden() throws Exception {
    User otherManager = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.MANAGER);
    deactivateDirectly(otherManager);

    command(manager, "/team/members/{id}/reactivate", otherManager).andExpect(status().isForbidden());
  }

  @Test
  void reactivate_anotherRestaurantsMember_is404() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);
    deactivateDirectly(staff);

    command(ownerR2, "/team/members/{id}/reactivate", staff).andExpect(status().isNotFound());
  }

  // ---- stale tokens (Gabriel, Build 1 slice 6) ----

  @Test
  void teamWrites_fromADeactivatedManagersStillValidToken_areForbidden() throws Exception {
    String managerToken = bearer(manager);
    command(owner, "/team/members/{id}/deactivate", manager).andExpect(status().isOk());

    command(managerToken, "/team/members/{id}/deactivate", staff).andExpect(status().isForbidden());
    mockMvc.perform(post("/team/members")
        .header("Authorization", managerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(inviteBody(uniqueEmail(), "STAFF")))
      .andExpect(status().isForbidden());
  }

  @Test
  void teamWrites_fromADemotedOwnersToken_areJudgedByTheCurrentRole() throws Exception {
    User coOwner = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);
    String coOwnerToken = bearer(coOwner);
    patchMember(owner, coOwner, Map.of("role", "MANAGER")).andExpect(status().isOk());

    // Still OWNER in the token; MANAGER now, so granting OWNER is refused.
    mockMvc.perform(post("/team/members")
        .header("Authorization", coOwnerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(inviteBody(uniqueEmail(), "OWNER")))
      .andExpect(status().isForbidden());
    command(coOwnerToken, "/team/members/{id}/deactivate", owner).andExpect(status().isForbidden());
  }

  // ---- resend invite ----

  @Test
  void resendInvite_pendingInvitee_emailsANewLink_andTheOldOneStopsWorking() throws Exception {
    invite(owner, uniqueEmail(), "STAFF").andExpect(status().isCreated());
    String firstToken = tokenOf(emails.invitations().get(0).resetUrl());
    User invitee = userRepository.findByEmailIgnoreCase(emails.invitations().get(0).toEmail()).orElseThrow();

    command(owner, "/team/members/{id}/resend-invite", invitee).andExpect(status().isNoContent());

    assertThat(emails.invitations()).hasSize(2);
    assertAudited(IdentityAuditEventType.INVITE_RESENT, owner, invitee.getId(), Map.of());
    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("token", firstToken, "newPassword", "Brand-new-pass1"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
  }

  @Test
  void resendInvite_memberWithAPassword_isInvitationNotPending() throws Exception {
    command(owner, "/team/members/{id}/resend-invite", staff)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
    assertThat(emails.invitations()).isEmpty();
  }

  @Test
  void resendInvite_deactivatedInvitee_isInvitationNotPending() throws Exception {
    User invitee = testUsers.inviteeOf(restaurantId, uniqueEmail(), MembershipRole.STAFF);
    deactivateDirectly(invitee);

    command(owner, "/team/members/{id}/resend-invite", invitee)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
  }

  @Test
  void resendInvite_managerForAManagerInvitee_isForbidden_forStaff_isAllowed() throws Exception {
    User managerInvitee = testUsers.inviteeOf(restaurantId, uniqueEmail(), MembershipRole.MANAGER);
    User staffInvitee = testUsers.inviteeOf(restaurantId, uniqueEmail(), MembershipRole.STAFF);

    command(manager, "/team/members/{id}/resend-invite", managerInvitee).andExpect(status().isForbidden());
    command(manager, "/team/members/{id}/resend-invite", staffInvitee).andExpect(status().isNoContent());
  }

  @Test
  void resendInvite_anotherRestaurantsInvitee_is404() throws Exception {
    User ownerR2 = testUsers.owner(uniqueEmail(), PASSWORD);
    User invitee = testUsers.inviteeOf(restaurantId, uniqueEmail(), MembershipRole.STAFF);

    command(ownerR2, "/team/members/{id}/resend-invite", invitee).andExpect(status().isNotFound());
    assertThat(emails.invitations()).isEmpty();
  }

  // ---- concurrency (D-16) ----

  @Test
  void twoOwnersDemotingEachOtherAtOnce_neverLeaveTheRestaurantWithoutAnOwner() throws Exception {
    User coOwner = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.OWNER);
    String ownerToken = bearer(owner);
    String coOwnerToken = bearer(coOwner);

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> demotions = List.of(
        () -> { start.await(); return patchMember(ownerToken, coOwner, Map.of("role", "MANAGER")).andReturn().getResponse().getStatus(); },
        () -> { start.await(); return patchMember(coOwnerToken, owner, Map.of("role", "MANAGER")).andReturn().getResponse().getStatus(); });
      List<Future<Integer>> results = new ArrayList<>();
      for (Callable<Integer> demotion : demotions) {
        results.add(pool.submit(demotion));
      }
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get());
      }
      // The loser is refused either by the owner count (409) or, if it starts after the
      // winner committed, by TeamAccess seeing it is now a manager (403).
      assertThat(statuses).containsOnlyOnce(200);
      assertThat(statuses).containsAnyOf(403, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(membershipRepository.countActiveOwners(restaurantId)).isEqualTo(1);
  }

  // ---- helpers ----

  private ResultActions invite(User actor, String email, String role) throws Exception {
    return mockMvc.perform(post("/team/members")
      .header("Authorization", bearer(actor))
      .contentType(MediaType.APPLICATION_JSON)
      .content(inviteBody(email, role)));
  }

  private String inviteBody(String email, String role) throws Exception {
    return objectMapper.writeValueAsString(Map.of("email", email, "firstName", "New", "lastName", "Member", "role", role));
  }

  private ResultActions patchMember(User actor, User target, Map<String, String> body) throws Exception {
    return patchMember(bearer(actor), target, body);
  }

  private ResultActions patchMember(String token, User target, Map<String, String> body) throws Exception {
    return mockMvc.perform(patch("/team/members/{id}", target.getId())
      .header("Authorization", token)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(body)));
  }

  private ResultActions command(User actor, String path, User target) throws Exception {
    return command(bearer(actor), path, target);
  }

  private ResultActions command(String token, String path, User target) throws Exception {
    return mockMvc.perform(post(path, target.getId()).header("Authorization", token));
  }

  private void rename(User user, String lastName) {
    User stored = userRepository.findById(user.getId()).orElseThrow();
    stored.setLastName(lastName);
    userRepository.save(stored);
  }

  private void deactivateDirectly(User user) {
    User stored = userRepository.findById(user.getId()).orElseThrow();
    stored.setActive(false);
    userRepository.save(stored);
  }

  private void assertAudited(IdentityAuditEventType type, User actor, String targetId, Map<String, Object> details) {
    assertThat(eventsOf(restaurantId))
      .as("exactly one %s event in the restaurant", type)
      .filteredOn(event -> event.getType() == type)
      .singleElement()
      .satisfies(event -> {
        assertThat(event.getActorUserId()).isEqualTo(actor.getId());
        assertThat(event.getTargetUserId()).isEqualTo(targetId);
        assertThat(event.getDetails()).isEqualTo(details);
      });
  }

  private List<IdentityAuditEvent> eventsOf(String restaurantId) {
    return auditEventRepository.findAll().stream()
      .filter(event -> restaurantId.equals(event.getRestaurantId()))
      .toList();
  }

  private static String tokenOf(String setupUrl) {
    return setupUrl.substring(setupUrl.indexOf("token=") + "token=".length());
  }

  private MvcResult login(User user) throws Exception {
    return mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(user.getEmail(), PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();
  }

  private String bearer(User user) throws Exception {
    JsonNode body = objectMapper.readTree(login(user).getResponse().getContentAsString());
    return "Bearer " + body.get("accessToken").asText();
  }

  private static String uniqueEmail() {
    return "team-" + UUID.randomUUID() + "@test.com";
  }
}
