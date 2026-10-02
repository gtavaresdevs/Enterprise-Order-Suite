package com.enterprise.ordersuite.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The root super admin: the anti-lockout account that cannot be deactivated, re-roled or
 * re-emailed.
 *
 * <p>Both secrets come from the environment and default to empty, so tests and fresh clones
 * need no secret to boot. When they are absent the account is simply not configured — the
 * seeder does nothing and {@link #matches(String)} protects nobody, rather than falling back
 * to a hardcoded address.
 */
@ConfigurationProperties(prefix = "app.root-super-admin")
public record RootSuperAdminProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String passwordHash,
        @DefaultValue("Root") String firstName,
        @DefaultValue("Super Admin") String lastName
) {

    /** Seeding needs both the address and a password hash; neither has a usable default. */
    public boolean isConfigured() {
        return !email.isBlank() && !passwordHash.isBlank();
    }

    /**
     * Whether the given address is the root account's. False when no root account is
     * configured — an unset property must not turn every address into the protected one,
     * nor a blank one into a match.
     */
    public boolean matches(String candidateEmail) {
        return !email.isBlank() && email.equalsIgnoreCase(candidateEmail);
    }
}
