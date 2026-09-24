package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.RefreshTokenService;
import com.enterprise.ordersuite.auth.service.tokens.TokenHashing;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RefreshTokenPersistenceIT {

  // Far in the past, so no token another test issues can fall on either side of it.
  private static final Instant EXPIRES_AT = Instant.parse("2020-01-28T12:00:00Z");

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  @Autowired
  private RefreshTokenService refreshTokenService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private Clock clock;

  @Test
  void save_storesExpiresAtAsTheInstantItWasGiven() {
    RefreshToken token = saveToken("raw-" + UUID.randomUUID(), EXPIRES_AT);

    OffsetDateTime stored = jdbcTemplate.queryForObject(
      "select expires_at from refresh_tokens where id = ?", OffsetDateTime.class, token.getId());

    assertThat(stored.toInstant())
      .as("the column must hold the instant itself, not a wall-clock reading in the JVM's zone")
      .isEqualTo(EXPIRES_AT);
  }

  @Test
  void deleteExpired_comparesTheStoredInstant() {
    RefreshToken token = saveToken("raw-" + UUID.randomUUID(), EXPIRES_AT);

    refreshTokenRepository.deleteExpired(EXPIRES_AT.minusSeconds(1));
    assertThat(refreshTokenRepository.findById(token.getId()))
      .as("one second before it expires, the token must survive cleanup")
      .isPresent();

    refreshTokenRepository.deleteExpired(EXPIRES_AT.plusSeconds(1));
    assertThat(refreshTokenRepository.findById(token.getId()))
      .as("one second after it expires, cleanup must remove it")
      .isEmpty();
  }

  // The reason D15 is in Phase 0: expiry was compared in an unstated zone. Around the
  // boundary, a 3-hour disagreement between writer and reader decides the answer.
  @Test
  void getActiveTokenOrNull_honoursExpiryWithinMinutes_notHours() {
    Instant now = clock.instant();
    String expiringSoon = "raw-" + UUID.randomUUID();
    String justExpired = "raw-" + UUID.randomUUID();
    saveToken(expiringSoon, now.plus(Duration.ofMinutes(5)));
    saveToken(justExpired, now.minus(Duration.ofMinutes(5)));

    assertThat(refreshTokenService.getActiveTokenOrNull(expiringSoon))
      .as("a token with five minutes left is active")
      .isNotNull();
    assertThat(refreshTokenService.getActiveTokenOrNull(justExpired))
      .as("a token that expired five minutes ago is not")
      .isNull();
  }

  private RefreshToken saveToken(String rawToken, Instant expiresAt) {
    User user = new User();
    user.setEmail("rt-" + UUID.randomUUID() + "@test.com");
    user.setPassword("not-a-real-hash");
    user.setRole(roleRepository.findByName("USER").orElseThrow());
    user.setActive(true);
    user.setFirstName("Refresh");
    user.setLastName("Token");
    userRepository.save(user);

    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setTokenHash(TokenHashing.sha256Hex(rawToken));
    token.setExpiresAt(expiresAt);
    return refreshTokenRepository.save(token);
  }
}
