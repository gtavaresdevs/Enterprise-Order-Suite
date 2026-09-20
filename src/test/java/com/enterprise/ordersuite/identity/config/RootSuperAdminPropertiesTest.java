package com.enterprise.ordersuite.identity.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RootSuperAdminPropertiesTest {

  @Test
  void matches_whenNoRootIsConfigured_protectsNobody() {
    RootSuperAdminProperties properties = properties("", "");

    assertThat(properties.matches("anyone@test.com"))
      .as("an unset root address must not turn an arbitrary account into the protected one")
      .isFalse();
  }

  @Test
  void matches_whenNoRootIsConfigured_doesNotMatchABlankAddress() {
    RootSuperAdminProperties properties = properties("", "");

    assertThat(properties.matches(""))
      .as("empty must not equal empty here, or a blank address would be the root account")
      .isFalse();
  }

  @Test
  void matches_ignoresCase() {
    RootSuperAdminProperties properties = properties("Root@Test.com", "hash");

    assertThat(properties.matches("root@test.com")).isTrue();
  }

  @Test
  void matches_aDifferentAddress_isFalse() {
    RootSuperAdminProperties properties = properties("root@test.com", "hash");

    assertThat(properties.matches("someone-else@test.com")).isFalse();
  }

  @Test
  void isConfigured_requiresBothTheAddressAndTheHash() {
    assertThat(properties("root@test.com", "hash").isConfigured()).isTrue();
    assertThat(properties("root@test.com", "").isConfigured()).isFalse();
    assertThat(properties("", "hash").isConfigured()).isFalse();
    assertThat(properties("", "").isConfigured()).isFalse();
  }

  private RootSuperAdminProperties properties(String email, String passwordHash) {
    return new RootSuperAdminProperties(email, passwordHash, "Root", "Super Admin");
  }
}
