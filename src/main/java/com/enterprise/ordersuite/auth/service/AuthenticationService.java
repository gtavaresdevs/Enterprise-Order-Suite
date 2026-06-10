package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final MeterRegistry meterRegistry;

  public AuthResponse register(RegisterRequest request) {

    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Email already in use.");
    }

    // SECURITY FIX: Hardcode the assignment to "USER".
    // Ignore any role the client might try to inject.
    Role role = roleRepository.findByName("USER")
      .orElseThrow(() -> new RuntimeException("Default role USER not found. Database seeded incorrectly."));

    User user = new User();
    user.setFirstName(request.getFirstName());
    user.setLastName(request.getLastName());
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setActive(true);
    user.setRole(role);

    userRepository.save(user);

    meterRegistry.counter("app.user.created", "source", "registration").increment();

    String accessToken = jwtService.generateToken(user);
    var issuedRefresh = refreshTokenService.issueFor(user);

    return new AuthResponse(accessToken, issuedRefresh.rawToken());
  }

  public AuthResponse authenticate(AuthRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
      .orElse(null);

    if (user == null || !Boolean.TRUE.equals(user.getActive()) ||
      !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      meterRegistry.counter("app.login.attempt", "status", "failure").increment();
      throw new InvalidCredentialsException();
    }

    meterRegistry.counter("app.login.attempt", "status", "success").increment();

    String accessToken = jwtService.generateToken(user);
    var issuedRefresh = refreshTokenService.issueFor(user);

    return new AuthResponse(accessToken, issuedRefresh.rawToken());
  }

  public AuthResponse refresh(RefreshRequest request) {
    RefreshToken existing = refreshTokenService.getActiveTokenOrNull(request.refreshToken());
    if (existing == null) {
      throw new InvalidRefreshTokenException();
    }

    User user = existing.getUser();

    if (!Boolean.TRUE.equals(user.getActive())) {
      refreshTokenService.revoke(existing);
      throw new InvalidRefreshTokenException();
    }

    refreshTokenService.markUsed(existing);

    String newAccessToken = jwtService.generateToken(user);
    var newRefresh = refreshTokenService.issueFor(user);

    return new AuthResponse(newAccessToken, newRefresh.rawToken());
  }

  public void logout(LogoutRequest request) {
    String hash = refreshTokenService.hash(request.refreshToken());
    RefreshToken token = refreshTokenService.findByHashOrNull(hash);

    if (token == null) {
      return;
    }

    refreshTokenService.revoke(token);
  }
}
