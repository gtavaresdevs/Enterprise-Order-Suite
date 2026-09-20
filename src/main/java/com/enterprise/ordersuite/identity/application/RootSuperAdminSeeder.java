package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.config.RootSuperAdminProperties;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the root super admin from the environment.
 *
 * <p>This replaces the user INSERT that used to live in {@code V15__seed_super_admin.sql},
 * which committed a real address and bcrypt hash into every environment the migration ran in.
 * The role INSERT stays in V15 — the roles are schema, not a secret.
 *
 * <p>Idempotent, and a no-op when either secret is absent, so tests and fresh clones boot
 * without one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RootSuperAdminSeeder implements ApplicationRunner {

  private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

  private final RootSuperAdminProperties properties;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.isConfigured()) {
      log.info("No root super admin configured (SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD_HASH unset) - skipping seed.");
      return;
    }

    if (userRepository.existsByEmailIgnoreCase(properties.email())) {
      log.debug("Root super admin already present - nothing to seed.");
      return;
    }

    Role superAdmin = roleRepository.findByName(SUPER_ADMIN_ROLE)
      .orElseThrow(() -> new IllegalStateException(
        "Role " + SUPER_ADMIN_ROLE + " is missing; V15 should have created it."));

    User user = new User();
    user.setFirstName(properties.firstName());
    user.setLastName(properties.lastName());
    user.setEmail(properties.email());
    // Already a bcrypt hash: the environment supplies the encoded value, never a plaintext
    // password this would then have to encode and log its way around.
    user.setPassword(properties.passwordHash());
    user.setActive(true);
    user.setRole(superAdmin);

    userRepository.save(user);

    log.info("Seeded the root super admin account.");
  }
}
