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

import java.util.Locale;

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
      // WARN, not INFO, and it names the consequence: an environment that ran the old V15
      // still has that seeded account, and with no SUPER_ADMIN_EMAIL set the anti-lockout
      // guard in UserAdminService now protects nothing. One SUPER_ADMIN can deactivate,
      // demote or re-email another, including the original root, with no way back.
      log.warn("No root super admin configured (SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD_HASH unset): "
        + "nothing was seeded, and NO account is protected against deactivation, role change or email change.");
      return;
    }

    String email = normalize(properties.email());

    if (userRepository.existsByEmailIgnoreCase(email)) {
      log.debug("Root super admin already present - nothing to seed.");
      return;
    }

    Role superAdmin = roleRepository.findByName(SUPER_ADMIN_ROLE)
      .orElseThrow(() -> new IllegalStateException(
        "Role " + SUPER_ADMIN_ROLE + " is missing; V15 should have created it."));

    User user = new User();
    user.setFirstName(properties.firstName());
    user.setLastName(properties.lastName());
    user.setEmail(email);
    // Already a bcrypt hash: the environment supplies the encoded value, never a plaintext
    // password this would then have to encode and log its way around.
    user.setPassword(properties.passwordHash());
    user.setActive(true);
    user.setRole(superAdmin);

    userRepository.save(user);

    log.info("Seeded the root super admin account.");
  }

  // Login matches the address exactly (AuthenticationService uses findByEmail, not the
  // ignore-case variant), so a SUPER_ADMIN_EMAIL carrying capitals or stray whitespace would
  // seed an account nobody can log into. Normalized the same way UserAdminService does.
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
