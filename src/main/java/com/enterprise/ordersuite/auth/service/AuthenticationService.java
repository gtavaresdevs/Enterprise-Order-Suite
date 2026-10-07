package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
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

@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository userRepository;
  private final UserRoleResolver userRoleResolver;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final MeterRegistry meterRegistry;

  public AuthResponse authenticate(AuthRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
      .orElse(null);

    if (user == null || !Boolean.TRUE.equals(user.getActive()) ||
      !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      meterRegistry.counter("app.login.attempt", "status", "failure").increment();
      throw new InvalidCredentialsException();
    }

    meterRegistry.counter("app.login.attempt", "status", "success").increment();

    String accessToken = accessTokenFor(user);
    var issuedRefresh = refreshTokenService.issueFor(user);

    return new AuthResponse(accessToken, issuedRefresh.rawToken());
  }

  // noRollbackFor: the reuse branch revokes the family and then answers 401. If the
  // exception rolled that back, reuse detection would silently do nothing.
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public AuthResponse refresh(String rawRefreshToken) {
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

    return new AuthResponse(accessTokenFor(user), rotated.rawToken());
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

  private String accessTokenFor(User user) {
    return jwtService.generateToken(user, userRoleResolver.roleOf(user).orElse(null));
  }
}
