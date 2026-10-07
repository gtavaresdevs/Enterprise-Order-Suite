package com.enterprise.ordersuite.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

  private final JwtProperties jwtProperties;
  private final Clock clock;

  public static final String RESTAURANT_ID_CLAIM = "rid";
  public static final String ROLE_CLAIM = "role";

  // Tenancy & Identity, Tokens: sub (user ULID), rid (restaurant ULID, absent for the
  // platform admin), role. No display data: the client reads it from GET /me.
  public String generateToken(String userId, String restaurantId, String role) {
    Map<String, Object> claims = new HashMap<>();
    if (restaurantId != null) {
      claims.put(RESTAURANT_ID_CLAIM, restaurantId);
    }
    if (role != null) {
      claims.put(ROLE_CLAIM, role);
    }
    Instant now = clock.instant();
    return Jwts.builder()
      .claims(claims)
      .subject(userId)
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusMillis(jwtProperties.getExpiration())))
      .signWith(getSignInKey())
      .compact();
  }

  public String extractUserId(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public String extractRestaurantId(String token) {
    return extractClaim(token, claims -> claims.get(RESTAURANT_ID_CLAIM, String.class));
  }

  public String extractRole(String token) {
    return extractClaim(token, claims -> claims.get(ROLE_CLAIM, String.class));
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  public boolean isTokenValid(String token) {
    return !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(Date.from(clock.instant()));
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser()
      .verifyWith(getSignInKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  private SecretKey getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
