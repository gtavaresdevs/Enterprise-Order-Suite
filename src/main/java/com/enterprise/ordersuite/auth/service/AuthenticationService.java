package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import com.enterprise.ordersuite.identity.application.UserRoleResolver;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository userRepository;
  private final UserRoleResolver userRoleResolver;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final MeterRegistry meterRegistry;

  public AuthTokens authenticate(AuthRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
      .orElse(null);

    if (user == null || !Boolean.TRUE.equals(user.getActive()) ||
      !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      meterRegistry.counter("app.login.attempt", "status", "failure").increment();
      throw new InvalidCredentialsException();
    }

    meterRegistry.counter("app.login.attempt", "status", "success").increment();

    var issuedRefresh = refreshTokenService.issueFor(user);

    return new AuthTokens(accessTokenFor(user, issuedRefresh.familyId()), issuedRefresh.rawToken());
  }

  // noRollbackFor: the reuse branch revokes the family and then answers 401. If the
  // exception rolled that back, reuse detection would silently do nothing.
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public AuthTokens refresh(String rawRefreshToken) {
    RefreshToken existing = refreshTokenService.findForRotationOrNull(rawRefreshToken);
    if (existing == null) {
      throw new InvalidRefreshTokenException();
    }

    // D16/D17: a used or revoked token presented again is reuse - kill the whole family.
    if (existing.isUsed() || existing.isRevoked()) {
      refreshTokenService.revokeFamily(existing);
      throw new InvalidRefreshTokenException();
    }

    if (refreshTokenService.isExpired(existing)) {
      throw new InvalidRefreshTokenException();
    }

    User user = existing.getUser();

    if (!Boolean.TRUE.equals(user.getActive())) {
      refreshTokenService.revokeFamily(existing);
      throw new InvalidRefreshTokenException();
    }

    var rotated = refreshTokenService.rotate(existing);

    return new AuthTokens(accessTokenFor(user, rotated.familyId()), rotated.rawToken());
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    // Locked lookup: serializes with a concurrent rotation of this token, so revokeFamily
    // below also revokes the successor that rotation inserted.
    RefreshToken token = refreshTokenService.findForRotationOrNull(rawRefreshToken);

    if (token == null) {
      return;
    }

    refreshTokenService.revokeFamily(token);
  }

  private String accessTokenFor(User user, UUID sessionId) {
    var acting = userRoleResolver.resolve(user);
    return jwtService.generateToken(
      user.getId(),
      acting.map(UserRoleResolver.ActingRole::restaurantId).orElse(null),
      acting.map(UserRoleResolver.ActingRole::role).orElse(null),
      sessionId.toString());
  }
}
