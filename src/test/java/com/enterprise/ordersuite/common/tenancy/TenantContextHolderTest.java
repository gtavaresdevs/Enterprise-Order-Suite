package com.enterprise.ordersuite.common.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

  private static final String RESTAURANT = "01J0000000000000000000000R";
  private static final String USER = "01J0000000000000000000000U";

  @AfterEach
  void clear() {
    TenantContextHolder.clear();
  }

  @Test
  void requireRestaurantId_withoutContext_failsClosed() {
    assertThatThrownBy(TenantContextHolder::requireRestaurantId)
      .isInstanceOf(TenantContextMissingException.class);
  }

  @Test
  void requireRestaurantId_forPlatformAdminWithoutSupportRead_failsClosed() {
    TenantContextHolder.set(new TenantContext(null, USER, "PLATFORM_ADMIN", false));

    assertThatThrownBy(TenantContextHolder::requireRestaurantId)
      .isInstanceOf(TenantContextMissingException.class);
  }

  @Test
  void requireRestaurantId_forMember_returnsTheirRestaurant() {
    TenantContextHolder.set(new TenantContext(RESTAURANT, USER, "STAFF", false));

    assertThat(TenantContextHolder.requireRestaurantId()).isEqualTo(RESTAURANT);
  }
}
