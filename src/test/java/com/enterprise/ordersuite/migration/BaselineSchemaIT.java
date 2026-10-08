package com.enterprise.ordersuite.migration;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The pre-launch baseline (ADR-0009): ULID keys, the restaurant as tenant, audit details as jsonb.
@IntegrationTest
class BaselineSchemaIT {

  private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private MembershipRepository membershipRepository;

  @Autowired
  private IdentityAuditEventRepository auditRepository;

  @Autowired
  private TestUsers testUsers;

  @Test
  void restaurant_getsAUlidId() {
    Restaurant saved = restaurantRepository.saveAndFlush(new Restaurant("Cantina", uniqueSlug(), "America/Sao_Paulo"));

    assertThat(saved.getId()).matches(ULID);
  }

  @Test
  void restaurant_slugOutsideThePattern_isRefusedByTheDatabase() {
    assertThatThrownBy(() -> restaurantRepository.saveAndFlush(
      new Restaurant("Cantina", "Not A Slug", "America/Sao_Paulo")))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void membership_isAtMostOnePerUser() {
    User owner = testUsers.owner(uniqueEmail(), "Password123!");
    Restaurant other = restaurantRepository.saveAndFlush(new Restaurant("Other", uniqueSlug(), "America/Sao_Paulo"));

    assertThatThrownBy(() -> membershipRepository.saveAndFlush(
      new Membership(other.getId(), owner, MembershipRole.STAFF)))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auditEvent_detailsRoundTripAsJson() {
    User owner = testUsers.owner(uniqueEmail(), "Password123!");
    String restaurantId = membershipRepository.findByUserId(owner.getId()).orElseThrow().getRestaurantId();

    IdentityAuditEvent saved = auditRepository.saveAndFlush(new IdentityAuditEvent(
      IdentityAuditEventType.ROLE_CHANGED, restaurantId, owner.getId(), owner.getId(),
      Map.of("from", "STAFF", "to", "MANAGER")));

    IdentityAuditEvent reloaded = auditRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getId()).matches(ULID);
    assertThat(reloaded.getDetails()).containsEntry("from", "STAFF").containsEntry("to", "MANAGER");
  }

  private String uniqueSlug() {
    return "r-" + UUID.randomUUID();
  }

  private String uniqueEmail() {
    return "baseline-" + UUID.randomUUID() + "@test.com";
  }
}
