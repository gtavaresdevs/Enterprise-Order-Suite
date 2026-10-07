package com.enterprise.ordersuite.security.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

  private static final String USER = "01J0000000000000000000000U";
  private static final String RESTAURANT = "01J0000000000000000000000R";

  private final JwtService jwtService = new JwtService(properties(), Clock.systemUTC());

  @Test
  void generateToken_forMember_carriesSubRidAndRole_andNoDisplayData() {
    String token = jwtService.generateToken(USER, RESTAURANT, "OWNER");

    assertThat(jwtService.extractUserId(token)).as("sub is the user ULID").isEqualTo(USER);
    assertThat(jwtService.extractRestaurantId(token)).isEqualTo(RESTAURANT);
    assertThat(jwtService.extractRole(token)).isEqualTo("OWNER");
    Set<String> claimNames = jwtService.extractClaim(token, (Claims claims) -> new HashSet<>(claims.keySet()));
    assertThat(claimNames)
      .as("display names and email leave the token; the client reads them from GET /me")
      .containsExactlyInAnyOrder("sub", "rid", "role", "iat", "exp");
  }

  @Test
  void generateToken_forPlatformAdmin_hasNoRestaurantClaim() {
    String token = jwtService.generateToken(USER, null, "PLATFORM_ADMIN");

    assertThat(jwtService.extractRestaurantId(token)).isNull();
    assertThat(jwtService.extractRole(token)).isEqualTo("PLATFORM_ADMIN");
  }

  private static JwtProperties properties() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(Base64.getEncoder()
      .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    properties.setExpiration(60_000);
    return properties;
  }
}
