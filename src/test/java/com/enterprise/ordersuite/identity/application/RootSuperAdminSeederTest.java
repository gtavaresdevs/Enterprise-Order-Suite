package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.config.RootSuperAdminProperties;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RootSuperAdminSeederTest {

  private static final String ROOT_EMAIL = "root@test.com";
  private static final String ROOT_HASH = "$2a$10$notarealhashbutshapedlikeone";

  @Mock
  private UserRepository userRepository;

  @Test
  void run_whenNothingIsConfigured_seedsNothing() {
    seeder(new RootSuperAdminProperties("", "", "Root", "Super Admin")).run(null);

    verifyNoInteractions(userRepository);
  }

  @Test
  void run_whenOnlyTheEmailIsConfigured_seedsNothing() {
    seeder(new RootSuperAdminProperties(ROOT_EMAIL, "", "Root", "Super Admin")).run(null);

    verifyNoInteractions(userRepository);
  }

  @Test
  void run_whenTheAccountAlreadyExists_seedsNothing() {
    when(userRepository.existsByEmailIgnoreCase(ROOT_EMAIL))
      .thenReturn(true);

    seeder(configured()).run(null);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void run_whenConfiguredAndAbsent_seedsAnActivePlatformAdminWithTheGivenHash() {
    when(userRepository.existsByEmailIgnoreCase(ROOT_EMAIL))
      .thenReturn(false);

    seeder(configured()).run(null);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());

    User seeded = captor.getValue();

    assertThat(seeded.getEmail()).isEqualTo(ROOT_EMAIL);
    assertThat(seeded.isPlatformAdmin()).isTrue();
    assertThat(seeded.getActive()).isTrue();

    assertThat(seeded.getPassword())
      .as("the environment supplies an encoded hash; the seeder must store it verbatim")
      .isEqualTo(ROOT_HASH);
  }

  @Test
  void run_normalizesTheConfiguredEmail() {
    when(userRepository.existsByEmailIgnoreCase(ROOT_EMAIL))
      .thenReturn(false);

    seeder(new RootSuperAdminProperties("  Root@Test.COM  ", ROOT_HASH, "Root", "Super Admin"))
      .run(null);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());

    assertThat(captor.getValue().getEmail())
      .as("login matches the address exactly, so an unnormalized seed cannot log in")
      .isEqualTo(ROOT_EMAIL);
  }

  private RootSuperAdminProperties configured() {
    return new RootSuperAdminProperties(ROOT_EMAIL, ROOT_HASH, "Root", "Super Admin");
  }

  private RootSuperAdminSeeder seeder(RootSuperAdminProperties properties) {
    return new RootSuperAdminSeeder(properties, userRepository);
  }
}
