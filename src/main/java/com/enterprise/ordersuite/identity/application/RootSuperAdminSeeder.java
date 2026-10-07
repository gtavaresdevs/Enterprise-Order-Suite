package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.config.RootSuperAdminProperties;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Seeds the root platform admin from the environment (Tenancy & Identity: the env-seeded
 * root user becomes the first platform admin).
 *
 * <p>Seeded at boot, not by a migration: a migration would commit a real address and bcrypt
 * hash into every environment it ran in.
 *
 * <p>Idempotent, and a no-op when either secret is absent, so tests and fresh clones boot
 * without one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RootSuperAdminSeeder implements ApplicationRunner {

  private final RootSuperAdminProperties properties;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.isConfigured()) {
      // WARN, not INFO: without it the environment has no platform admin, so nobody can
      // create a restaurant.
      log.warn("No root platform admin configured (SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD_HASH unset): "
        + "nothing was seeded, and this environment has no platform admin.");
      return;
    }

    String email = normalize(properties.email());

    if (userRepository.existsByEmailIgnoreCase(email)) {
      log.debug("Root platform admin already present - nothing to seed.");
      return;
    }

    User user = new User();
    user.setFirstName(properties.firstName());
    user.setLastName(properties.lastName());
    user.setEmail(email);
    // Already a bcrypt hash: the environment supplies the encoded value, never a plaintext
    // password this would then have to encode and log its way around.
    user.setPassword(properties.passwordHash());
    user.setActive(true);
    user.setPlatformAdmin(true);

    userRepository.save(user);

    log.info("Seeded the root platform admin account.");
  }

  // Login matches the address exactly (AuthenticationService uses findByEmail, not the
  // ignore-case variant), so a SUPER_ADMIN_EMAIL carrying capitals or stray whitespace would
  // seed an account nobody can log into. Emails are stored lowercase (API conventions §8.4).
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
