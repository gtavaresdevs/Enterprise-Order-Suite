package com.enterprise.ordersuite.security.jwt;

import com.enterprise.ordersuite.identity.domain.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

  @Test
  void generateToken_carriesDisplayIdentityClaims_andKeepsTheExistingOnes() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(Base64.getEncoder()
      .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    properties.setExpiration(60_000);
    JwtService jwtService = new JwtService(properties, Clock.systemUTC());

    User user = new User();
    user.setId("01J0000000000000000000000A");
    user.setEmail("ana@test.com");
    user.setFirstName("Ana");
    user.setLastName("Souza");

    String token = jwtService.generateToken(user, "OWNER");

    var firstName = jwtService.<String>extractClaim(token, c -> c.get("firstName", String.class));
    var lastName = jwtService.<String>extractClaim(token, c -> c.get("lastName", String.class));
    var email = jwtService.<String>extractClaim(token, c -> c.get("email", String.class));

    assertThat(firstName).isEqualTo("Ana");
    assertThat(lastName).isEqualTo("Souza");
    assertThat(email).isEqualTo("ana@test.com");
    assertThat(jwtService.extractEmail(token)).as("sub is unchanged").isEqualTo("ana@test.com");
    assertThat(jwtService.extractUserId(token)).isEqualTo("01J0000000000000000000000A");
    assertThat(jwtService.extractRoles(token)).isEqualTo(List.of("OWNER"));
  }
}
